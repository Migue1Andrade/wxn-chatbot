import { readFileSync } from 'node:fs';

const envFile = process.argv[2] || '.env';
const configuration = Object.fromEntries(
  readFileSync(envFile, 'utf8')
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('#') && line.includes('='))
    .map((line) => {
      const separator = line.indexOf('=');
      let value = line.slice(separator + 1).trim();
      if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
        value = value.slice(1, -1);
      }
      return [line.slice(0, separator).trim(), value];
    }),
);
const profiles = (configuration.SPRING_PROFILES_ACTIVE || '').split(/[,\s]+/);
const port = Number(configuration.BACKEND_PORT || 4001);
if (
  !profiles.includes('dev') ||
  profiles.includes('prod') ||
  !Number.isInteger(port) ||
  port < 1 ||
  port > 65535 ||
  (configuration.DEV_API_KEY || '').length < 32
) {
  throw new Error('Seed permitido somente no ambiente local dev com chave configurada.');
}
// Explicit local demonstration credentials; never imported by the application or migrations.
const accounts = [
  {
    username: 'Administrador',
    password: 'pereba02',
    name: 'Administrador WXN',
    email: 'mfrancadeandrade@gmail.com',
    cpf: '73184290603',
    phone: null,
    role: 'ADMIN',
    accountStatus: 'ACTIVE',
    departmentIds: [],
  },
  {
    username: 'Atendente',
    password: 'pereba02',
    name: 'Atendente WXN',
    email: 'returnsqnz@gmail.com',
    cpf: '58419372005',
    phone: null,
    role: 'ATTENDANT',
    accountStatus: 'ACTIVE',
    departmentIds: [],
  },
].map((account) => ({ ...account, passwordConfirmation: account.password }));
try {
  const response = await fetch(`http://127.0.0.1:${port}/api/dev/users/seed`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Dev-Api-Key': configuration.DEV_API_KEY },
    body: JSON.stringify(accounts),
  });
  const result = await response.json();
  if (!response.ok) {
    throw new Error(result.detail || `Seed rejeitado: HTTP ${response.status}`);
  }
  console.log(JSON.stringify(result, null, 2));
} catch (error) {
  console.error(
    error.cause?.code
      ? `Seed não executado: conexão local indisponível (${error.cause.code}).`
      : error.message,
  );
  process.exitCode = 1;
}
