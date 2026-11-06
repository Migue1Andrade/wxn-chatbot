import { existsSync, readFileSync } from 'node:fs';

function refuse(message) {
  console.error('Seed não executado: ' + message);
  process.exit(1);
}

const file = process.env.WXN_ENV_FILE || '.env';
const local = {};
if (existsSync(file)) {
  for (const line of readFileSync(file, 'utf8').split('\n')) {
    const match = line.match(/^\s*(?:export\s+)?([A-Z_][A-Z0-9_]*)\s*=\s*(.*?)\s*$/);
    if (match) {
      local[match[1]] = match[2].replace(/^(['"])(.*)\1$/, '$2');
    }
  }
}
const env = { ...local, ...process.env };
const profiles = (env.SPRING_PROFILES_ACTIVE || '').split(',').map((value) => value.trim());
const base = new URL(env.WXN_DEMO_BASE_URL || `http://localhost:${env.BACKEND_PORT || 4001}`);
if (
  !profiles.includes('dev') ||
  profiles.includes('prod') ||
  base.protocol !== 'http:' ||
  !['localhost', '127.0.0.1'].includes(base.hostname) ||
  base.username ||
  base.password ||
  base.pathname !== '/'
) {
  refuse('Exige perfil dev sem prod e backend HTTP em loopback local.');
}
const username = env.WXN_SEED_USERNAME;
const password = env.WXN_SEED_PASSWORD;
const demoPassword = env.WXN_DEMO_PASSWORD;
if (
  !username ||
  !password ||
  !demoPassword ||
  demoPassword.length < 8 ||
  demoPassword.length > 72 ||
  !env.DEV_API_KEY
) {
  refuse(
    'Configure WXN_SEED_USERNAME, WXN_SEED_PASSWORD, WXN_DEMO_PASSWORD (8–72 caracteres) e DEV_API_KEY. Senhas não serão exibidas nem alteradas em contas existentes.',
  );
}
let cookie = '';
let csrf = '';
async function api(method, path, body) {
  const response = await fetch(new URL(path, base), {
    method,
    redirect: 'error',
    signal: AbortSignal.timeout(180000),
    headers: {
      'Content-Type': 'application/json',
      'X-Dev-Api-Key': env.DEV_API_KEY,
      ...(cookie ? { Cookie: cookie } : {}),
      ...(csrf ? { 'X-CSRF-Token': csrf } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const sessionCookie = response.headers.get('set-cookie');
  if (sessionCookie) {
    cookie = sessionCookie.split(';', 1)[0];
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${data.detail || 'Operação recusada pelo servidor'}`);
  }
  if (data.csrfToken) {
    csrf = data.csrfToken;
  }
  return data;
}
try {
  await api('GET', '/api/auth/session');
  const session = await api('POST', '/api/auth/login', { username, password });
  if (session.user?.role !== 'ADMIN') {
    throw new Error('Seed exige uma conta ADMIN existente.');
  }
  const report = await api('POST', '/api/dev/demo', { password: demoPassword });
  console.log(JSON.stringify(report, null, 2));
  if (report.failures.length) {
    throw new Error('Seed parcial: há falhas no relatório. Os recursos anteriores foram preservados.');
  }
  if (process.argv.includes('--verify-idempotency')) {
    const second = await api('POST', '/api/dev/demo', { password: demoPassword });
    const created = Object.values(second.resources).reduce((sum, row) => sum + row.created, 0);
    console.log(
      JSON.stringify(
        { verification: 'segunda execução', created, resources: second.resources, failures: second.failures },
        null,
        2,
      ),
    );
    if (created || second.failures.length) {
      throw new Error('A segunda execução não confirmou idempotência.');
    }
  }
  await api('POST', '/api/auth/logout');
} catch (error) {
  const code = error.cause?.code;
  console.error('Seed não concluído: ' + (code ? `conexão local indisponível (${code})` : error.message));
  process.exitCode = 1;
}
