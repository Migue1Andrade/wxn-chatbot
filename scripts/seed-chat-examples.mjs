import { readFileSync } from 'node:fs';

const envFile = process.argv[2] || '.env';
const vars = Object.fromEntries(readFileSync(envFile, 'utf8').split('\n').filter(line => line && !line.startsWith('#')).map(line => {
  const index = line.indexOf('='); return [line.slice(0, index), line.slice(index + 1).trim()];
}));
if (!(vars.SPRING_PROFILES_ACTIVE || '').split(',').includes('dev') || (vars.SPRING_PROFILES_ACTIVE || '').includes('prod')) {
  throw new Error('Exemplos permitidos somente no ambiente local dev.');
}
const companyId = Number(vars.DEV_COMPANY_ID || 1);
const base = `http://localhost:${vars.BACKEND_PORT || 4001}`;
const prefix = `/api/companies/${companyId}`;
async function api(method, path, body) {
  const response = await fetch(base + path, { method, headers: { 'Content-Type': 'application/json', 'X-Dev-Api-Key': vars.DEV_API_KEY }, body: body ? JSON.stringify(body) : undefined });
  if (!response.ok) throw new Error(`${method} ${path}: HTTP ${response.status}`);
  return response.json();
}
await api('GET', prefix);
let members = await api('GET', prefix + '/members');
let users = await api('GET', prefix + '/users');
async function human(name) {
  let member = members.find(m => m.name === name && m.type === 'HUMAN' && m.status === 'ACTIVE');
  if (member) return member;
  let user = users.find(u => u.name === name);
  if (!user) { user = await api('POST', prefix + '/users', { name }); users.push(user); }
  member = await api('POST', prefix + '/members', { name, type: 'HUMAN', userId: user.id }); members.push(member); return member;
}
async function bot(name) {
  let member = members.find(m => m.name === name && m.type === 'BOT' && m.status === 'ACTIVE');
  if (!member) { member = await api('POST', prefix + '/members', { name, type: 'BOT' }); members.push(member); }
  return member;
}
const primary = members.find(m => m.type === 'HUMAN' && m.status === 'ACTIVE' && users.some(u => u.id === m.userId)) || await human('João Atendimento');
const secondary = await human('Ana Suporte');
const commercial = await bot('Bot Comercial');
const finance = await bot('Bot Financeiro');
const channels = await api('GET', prefix + '/whatsapp/channels');
const channel = channels.find(c => c.provider === 'MOCK' && c.status === 'ACTIVE' && c.phoneNumber) ||
  await api('POST', prefix + '/whatsapp/channels', { name: 'Mock de exemplos', provider: 'MOCK', phoneNumber: '+14155238886' });
async function inbound(number, content, key) {
  return api('POST', '/api/dev/whatsapp/mock/inbound', { companyId, channelId: channel.id, externalMessageId: `wxn-examples-v1-${key}`, senderPhone: number, content });
}
async function change(conversation, action, destination, reason) {
  return api('PATCH', prefix + `/conversations/${conversation.id}/${action}`, { actorMemberId: primary.id,
    expectedVersion: conversation.version, ...(destination ? { memberId: destination.id } : {}), reason });
}
async function reply(conversation, member, text) {
  return api('POST', prefix + `/conversations/${conversation.id}/messages`, { channelId: channel.id, memberId: member.id, content: text });
}
const samples = [
  ['Pendente / não lida', '+5581991000001', 'Exemplo: estou aguardando um atendente.'],
  ['Em atendimento HUMAN / lida', '+5581991000002', 'Exemplo: preciso de ajuda com meu pedido.'],
  ['Em atendimento BOT', '+5581991000003', 'Exemplo: atendimento atribuído ao Bot Comercial, sem IA.'],
  ['Transferências HUMAN e BOT', '+5581991000004', 'Exemplo: veja as transferências no histórico deste atendimento.'],
  ['Atendimento encerrado', '+5581991000005', 'Exemplo: solicitação resolvida; atendimento finalizado.'],
  ['Reabertura manual', '+5581991000006', 'Exemplo: atendimento encerrado e reaberto manualmente.'],
  ['Reabertura automática', '+5581991000007', 'Exemplo: nova mensagem reabre um atendimento finalizado.'],
  ['Paginação / 36 mensagens', '+5581991000008', 'Exemplo de paginação — mensagem 01 de 36.']
];
const results = [];
for (let index = 0; index < samples.length; index++) {
  const [label, phone, text] = samples[index];
  const received = await inbound(phone, text, `case-${index + 1}-initial`);
  let conversation = await api('GET', prefix + '/conversations/' + received.conversationId);
  // Preserve any manual changes when the sample command is run again.
  if (conversation.version === 0) {
    if (index === 1) {
      conversation = await change(conversation, 'assign', primary, 'Exemplo de atribuição humana');
      const sent = await reply(conversation, primary, 'Exemplo: olá! Vou acompanhar seu pedido.');
      await api('POST', prefix + `/conversations/${conversation.id}/read`, { memberId: primary.id, throughMessageId: sent.id });
    }
    if (index === 2) conversation = await change(conversation, 'assign', commercial, 'Exemplo de atribuição a BOT');
    if (index === 3) {
      conversation = await change(conversation, 'assign', primary, 'Início do atendimento');
      for (const destination of [secondary, commercial, finance, primary]) conversation = await change(conversation, 'transfer', destination, 'Transferência demonstrativa');
      await reply(conversation, primary, 'Exemplo: atendimento transferido e retomado pelo humano.');
    }
    if (index === 4 || index === 5 || index === 6) {
      conversation = await change(conversation, 'assign', index === 6 ? finance : (index === 4 ? secondary : primary), 'Exemplo de responsável');
      conversation = await change(conversation, 'close', null, 'Exemplo de encerramento');
      if (index === 5) conversation = await change(conversation, 'reopen', null, 'Cliente pediu nova análise');
      if (index === 6) await inbound(phone, 'Exemplo: voltei com uma nova dúvida. O atendimento foi reaberto automaticamente.', 'case-7-auto-reopen');
    }
  }
  if (index === 7) {
    for (let message = 2; message <= 36; message++) await inbound(phone, `Exemplo de paginação — mensagem ${String(message).padStart(2, '0')} de 36. Role até o topo para carregar as anteriores.`, `pagination-${message}`);
  }
  conversation = await api('GET', prefix + '/conversations/' + received.conversationId);
  results.push({ exemplo: label, telefone: phone, status: conversation.status, responsavel: conversation.assignedMemberId, conversa: conversation.id });
}
console.log(JSON.stringify({ companyId, primaryHuman: primary.name, examples: results }, null, 2));
