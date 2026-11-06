# Evolution API + Baileys

A integração ativa do WXN usa a Evolution API `v2.3.7` com engine `WHATSAPP-BAILEYS`.
O fluxo é:

```text
WhatsApp -> Evolution API -> Spring Boot -> MongoDB -> WebSocket/STOMP -> AngularJS
```

A execução recomendada usa todos os serviços no Docker Compose. Nesse modo, a Evolution chama
`http://backend:4001/api/webhooks/evolution` pela rede interna e não é necessário ngrok para o
primeiro teste local. O ngrok fica opcional para receber callbacks de fora da máquina.

## Configuração

Copie `.env.example` para `.env` e preencha, no mínimo:

```dotenv
EVOLUTION_API_KEY=uma-chave-local-com-longo-valor
EVOLUTION_INSTANCE_NAME=wxn-local
EVOLUTION_WEBHOOK_URL=https://seu-tunel.example/api/webhooks/evolution
EVOLUTION_WEBHOOK_SECRET=um-segredo-com-pelo-menos-32-caracteres
WXN_BOOTSTRAP_TOKEN=outro-segredo-com-pelo-menos-32-caracteres
EVOLUTION_POSTGRES_PASSWORD=senha-local
```

Use chaves diferentes para API, webhook e bootstrap. Elas não devem ser commitadas nem
colocadas no frontend. Em desenvolvimento, a aplicação também mantém a proteção existente
`X-Dev-Api-Key`.

## Iniciar de forma simples

Depois de preencher o `.env`, execute um único comando na raiz do projeto:

```bash
npm run dev:compose
```

Isso inicia PostgreSQL, MongoDB, Redis, Evolution API, backend e frontend. A tela fica disponível
em `http://localhost:4002` e a API em `http://localhost:4001`.

Para executar os serviços separadamente durante diagnóstico, ainda estão disponíveis
`npm run dev:infra`, `npm run dev:backend`, `npm run dev:frontend` e `npm run dev:ngrok`.

O Compose carrega o `.env` automaticamente e usa nomes internos de serviço para a comunicação.

Para uma apresentação acadêmica sem conexão WhatsApp, defina temporariamente
`VITE_SKIP_WHATSAPP_SETUP=true` no `.env`, reinicie o frontend e execute:

```bash
npm run seed:presentation
```

Esse modo abre diretamente o login e popula os dados de demonstração existentes. Ele é apenas
para desenvolvimento local; volte a `false` quando retomar o onboarding real.

## Iniciar manualmente

```bash
docker compose up -d evolution-postgres evolution-redis evolution-api
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm run dev
```

Abra `http://localhost:4002`. Enquanto a configuração inicial estiver pendente, a aplicação
redireciona para `/setup/whatsapp`. Informe o `WXN_BOOTSTRAP_TOKEN`, clique em **Gerar QR Code**
e escaneie em **WhatsApp > Dispositivos conectados > Conectar dispositivo**. Depois de o backend
confirmar o estado `open`, a tela mostra o sucesso e vai para `/login`.

A instância é criada apenas se ainda não existir. A sessão fica no volume Docker
`evolution_instances` e sobrevive ao fechamento do navegador e à reinicialização da API.

## Webhook e mensagens

O endpoint interno é `POST /api/webhooks/evolution`. A Evolution envia eventos de conexão,
QR Code, recebimento, envio e atualização de status. O backend valida o segredo no header,
normaliza primeiro mensagens de texto, aplica idempotência, persiste no MongoDB e publica o
evento na Inbox via STOMP.

O envio é feito pelo endpoint existente de mensagens da WXN; o backend chama
`/message/sendText/{instance}` da Evolution. A aceitação HTTP é registrada como `ACCEPTED` e
os eventos posteriores atualizam entrega, leitura ou falha quando disponíveis.

## Reconexão e limitações

Se o WhatsApp cair depois do onboarding, o login continua disponível. A reconexão deve ser
feita por um administrador nas configurações; atendentes não recebem o QR Code administrativo.
Esta primeira etapa prioriza texto individual. Grupos e mídia ainda não são declarados como
suportados. Baileys utiliza WhatsApp Web, não a API oficial da Meta; a conta pode sofrer
desconexões ou restrições de acordo com as políticas e alterações do WhatsApp.

## Limpeza local

O script abaixo cria backup PostgreSQL/MongoDB, mostra contagens e só remove dados operacionais
com confirmação explícita. Ele preserva usuários, departamentos, regras, respostas rápidas,
configurações e índices; não usa `TRUNCATE`, `DROP`, `FLUSHALL` nem roda automaticamente.

```bash
CONFIRM_WXN_LOCAL_CLEANUP=DELETE_OPERATIONAL_DATA \
  WXN_POSTGRES_URL=jdbc:postgresql://localhost:4003/wxn_chatbot \
  WXN_MONGODB_URI=mongodb://localhost:4004/wxn_chatbot \
  ./scripts/clean-local-operational-data.sh
```

O script recusa perfis de produção/staging e URLs não locais. Guarde o diretório de backup
informado ao final.

## Testes

```bash
./mvnw -DskipTests package
cd frontend && npm run build && npm run lint
```

Os testes automatizados usam fixtures/mocks e não substituem o teste real. A validação real
começa quando uma pessoa escaneia o QR Code e envia uma mensagem de outro número.
