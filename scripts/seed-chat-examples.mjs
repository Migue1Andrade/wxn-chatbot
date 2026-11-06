import { readFileSync } from 'node:fs';

const envFile = process.argv[2] || '.env';
const vars = Object.fromEntries(
  readFileSync(envFile, 'utf8')
    .split('\n')
    .filter((line) => line && !line.startsWith('#'))
    .map((line) => {
      const index = line.indexOf('=');
      return [line.slice(0, index), line.slice(index + 1).trim()];
    }),
);
if (
  !(vars.SPRING_PROFILES_ACTIVE || '').split(',').includes('dev') ||
  (vars.SPRING_PROFILES_ACTIVE || '').includes('prod')
) {
  throw new Error('Exemplos permitidos somente no ambiente local dev.');
}
const base = `http://localhost:${vars.BACKEND_PORT || 4001}`;
const prefix = '/api';
let cookie = '';
let csrfToken = '';
async function api(method, path, body) {
  const response = await fetch(base + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      'X-Dev-Api-Key': vars.DEV_API_KEY,
      ...(cookie ? { Cookie: cookie } : {}),
      ...(csrfToken ? { 'X-CSRF-Token': csrfToken } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    throw new Error(`${method} ${path}: HTTP ${response.status}`);
  }
  const sessionCookie = response.headers.get('set-cookie');
  if (sessionCookie) {
    cookie = sessionCookie.split(';', 1)[0];
  }
  const result = await response.json();
  if (result.csrfToken) {
    csrfToken = result.csrfToken;
  }
  return result;
}
if (!process.env.WXN_SEED_USERNAME || !process.env.WXN_SEED_PASSWORD) {
  throw new Error(
    'Execute primeiro seed-local-users.mjs; informe WXN_SEED_USERNAME e WXN_SEED_PASSWORD de um ADMIN para os exemplos de conversas.',
  );
}
await api('GET', '/api/auth/session');
const login = await api('POST', '/api/auth/login', {
  username: process.env.WXN_SEED_USERNAME,
  password: process.env.WXN_SEED_PASSWORD,
});
if (login.user.role !== 'ADMIN') {
  throw new Error('Exemplos de conversas exigem ADMIN.');
}
const members = await api('GET', prefix + '/members');
const departments = await api('GET', prefix + '/departments');
const selectedDepartments = {};
for (const [name, color] of [
  ['Comercial', '#2563EB'],
  ['Suporte', '#16A34A'],
  ['Financeiro', '#9333EA'],
  ['Pós-venda', '#EA580C'],
]) {
  let department = departments.find(
    (item) => item.name.toLocaleLowerCase('pt-BR') === name.toLocaleLowerCase('pt-BR'),
  );
  if (!department) {
    department = await api('POST', prefix + '/departments', { name, color });
    departments.push(department);
  }
  selectedDepartments[name] = department;
}
async function bot(name) {
  let member = members.find((m) => m.name === name && m.type === 'BOT' && m.status === 'ACTIVE');
  if (!member) {
    member = await api('POST', prefix + '/members', { name, type: 'BOT' });
    members.push(member);
  }
  return member;
}
const primary = members.find(
  (m) => m.type === 'HUMAN' && m.status === 'ACTIVE' && m.accountStatus === 'ACTIVE',
);
if (!primary) {
  throw new Error('Cadastre o Atendente pelo seed-local-users.mjs antes de criar exemplos.');
}
const secondary = primary;
const commercial = await bot('Bot Comercial');
const finance = await bot('Bot Financeiro');
const channels = await api('GET', prefix + '/whatsapp/channels');
const channelName = 'WXN Demonstração';
const channel =
  channels.find(
    (c) => c.name === channelName && c.provider === 'MOCK' && c.status === 'ACTIVE' && c.phoneNumber,
  ) ||
  (await api('POST', prefix + '/whatsapp/channels', {
    name: channelName,
    provider: 'MOCK',
    phoneNumber: '+12025550100',
  }));
async function inbound(number, content, key, senderName) {
  return api('POST', '/api/dev/whatsapp/mock/inbound', {
    channelId: channel.id,
    externalMessageId: `wxn-examples-v2-${key}`,
    senderPhone: number,
    senderName,
    content,
  });
}
async function change(conversation, action, destination, reason) {
  return api('PATCH', prefix + `/conversations/${conversation.id}/${action}`, {
    actorMemberId: login.user.memberId,
    expectedVersion: conversation.version,
    ...(destination ? { memberId: destination.id } : {}),
    reason,
  });
}
async function reply(conversation, member, text) {
  return api('POST', prefix + `/conversations/${conversation.id}/messages`, {
    operationId: crypto.randomUUID(),
    channelId: channel.id,
    memberId: member.id,
    content: text,
  });
}
const samples = [
  ['Pendente / não lida', '+12025550101', 'Exemplo: estou aguardando um atendente.'],
  ['Em atendimento HUMAN / lida', '+12025550102', 'Exemplo: preciso de ajuda com meu pedido.'],
  ['Em atendimento BOT', '+12025550103', 'Exemplo: atendimento atribuído ao Bot Comercial, sem IA.'],
  [
    'Transferências HUMAN e BOT',
    '+12025550104',
    'Exemplo: veja as transferências no histórico deste atendimento.',
  ],
  ['Atendimento encerrado', '+12025550105', 'Exemplo: solicitação resolvida; atendimento finalizado.'],
  ['Reabertura manual', '+12025550106', 'Exemplo: atendimento encerrado e reaberto manualmente.'],
  ['Reabertura automática', '+12025550107', 'Exemplo: nova mensagem reabre um atendimento finalizado.'],
  ['Paginação / 36 mensagens', '+12025550108', 'Exemplo de paginação — mensagem 01 de 36.'],
];
const contactNames = [
  'Pedro Lima',
  'Juliana Souza',
  'Lucas Ferreira',
  'Beatriz Rocha',
  'Rafael Gomes',
  'Camila Ribeiro',
  'Gabriel Alves',
  'Fernanda Martins',
];
const results = [];
for (let index = 0; index < samples.length; index++) {
  const [label, phone, text] = samples[index];
  const current = await api('GET', prefix + '/conversations?size=200&search=' + encodeURIComponent(phone));
  const alreadyExists = current.some((item) => item.channelId === channel.id && item.contactPhone === phone);
  const received = await inbound(phone, text, `case-${index + 1}-initial`, contactNames[index]);
  let conversation = await api('GET', prefix + '/conversations/' + received.conversationId);
  // Preserve any manual changes when the sample command is run again.
  if (!alreadyExists) {
    if (index === 1) {
      conversation = await change(conversation, 'assign', primary, 'Exemplo de atribuição humana');
      const sent = await reply(conversation, primary, 'Exemplo: olá! Vou acompanhar seu pedido.');
      await api('POST', prefix + `/conversations/${conversation.id}/read`, {
        memberId: primary.id,
        throughMessageId: sent.id,
      });
    }
    if (index === 2) {
      conversation = await change(conversation, 'assign', commercial, 'Exemplo de atribuição a BOT');
    }
    if (index === 3) {
      conversation = await change(conversation, 'assign', primary, 'Início do atendimento');
      for (const destination of [commercial, finance, primary]) {
        conversation = await change(conversation, 'transfer', destination, 'Transferência demonstrativa');
      }
      await reply(conversation, primary, 'Exemplo: atendimento transferido e retomado pelo humano.');
    }
    if (index === 4 || index === 5 || index === 6) {
      conversation = await change(
        conversation,
        'assign',
        index === 6 ? finance : index === 4 ? secondary : primary,
        'Exemplo de responsável',
      );
      conversation = await change(conversation, 'close', null, 'Exemplo de encerramento');
      if (index === 5) {
        await change(conversation, 'reopen', null, 'Cliente pediu nova análise');
      }
      if (index === 6) {
        await inbound(
          phone,
          'Exemplo: voltei com uma nova dúvida. O atendimento foi reaberto automaticamente.',
          'case-7-auto-reopen',
        );
      }
    }
  }
  if (index === 7) {
    for (let message = 2; message <= 36; message++) {
      await inbound(
        phone,
        `Exemplo de paginação — mensagem ${String(message).padStart(2, '0')} de 36. Role até o topo para carregar as anteriores.`,
        `pagination-${message}`,
      );
    }
  }
  conversation = await api('GET', prefix + '/conversations/' + received.conversationId);
  results.push({
    exemplo: label,
    telefone: phone,
    status: conversation.status,
    responsavel: conversation.assignedMemberId,
    conversa: conversation.id,
  });
}
console.log(
  JSON.stringify(
    {
      channel: { id: channel.id, name: channel.name, provider: channel.provider },
      departments: Object.values(selectedDepartments).map(({ id, name, color }) => ({ id, name, color })),
      attendants: members
        .filter((member) => member.type === 'HUMAN')
        .map(({ id, name, status, departments }) => ({ id, name, status, departments })),
      examples: results,
    },
    null,
    2,
  ),
);
