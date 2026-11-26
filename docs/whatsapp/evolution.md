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

## Piloto de suporte WXN Carros

O piloto é opcional e fica desligado por padrão (`OPENBOT_SUPPORT_ENABLED=false`). Com a flag
ativa, o backend atende mensagens individuais de texto em conversas atribuídas a um BOT ativo.
Para atribuir conversas pendentes automaticamente, configure o par
`OPENBOT_SUPPORT_AUTO_ASSIGN_CHANNEL_ID` e `OPENBOT_SUPPORT_AUTO_ASSIGN_BOT_ID`.
A atribuição automática vale para o canal configurado e mantém responsáveis já atribuídos.

Com `OPENBOT_SUPPORT_WEBHOOK_URL` e `OPENBOT_SUPPORT_HMAC_SECRET`, o backend envia ao OpenBot
um evento assíncrono após persistir a mensagem. O corpo contém
`{type:"wxn.support.message",data:{message,context,history,requestId}}`. A FAQ fictícia cobre test-drive,
documentação, financiamento, preço e pós-venda. `history` contém até oito mensagens recentes, apenas texto
e papel `customer`/`assistant`, limitadas a 300 caracteres cada e da mesma conversa. Telefone e metadados
de contato não são enviados.

O webhook usa `x-openbot-timestamp` (Unix em segundos), `x-openbot-delivery-id` (ID estável por
canal e mensagem externa) e `x-openbot-signature` (HMAC-SHA256 sobre
`timestamp.deliveryId.corpo JSON exato`). O agente devolve o resultado por
`POST /api/openbot/support/replies`, com o header `x-openbot-service-token` e JSON
`{"requestId":"...","reply":"...","handoff":false,"askResolution":false}`. `askResolution` é opcional;
quando verdadeiro, o backend acrescenta “Todas as suas dúvidas foram solucionadas?” e só encerra como
`RESOLVED` após uma confirmação positiva do cliente. Quando o campo é omitido, o backend pergunta após respostas cobertas pela FAQ, exceto saudações.
Respostas negativas ou ambíguas continuam o atendimento.
Repetição concluída retorna `204`, solicitação inexistente/expirada retorna `410` e retorno em processamento
retorna `409`.

Após responder, o bot marca **Aguardando cliente** se continua responsável e a mensagem ainda é a
entrada mais recente. A próxima mensagem do cliente reabre o atendimento em andamento.
`handoff:true` libera a conversa do BOT para a fila `PENDING` e cria alertas genéricos
`CONVERSATION_AVAILABLE` para contas ativas com membro HUMAN. O alerta contém apenas o ID da
conversa como recurso, sem texto ou dados de contato. `POST /api/notifications/{id}/claim` não
recebe corpo; requer sessão e CSRF, devolve a conversa atribuída em `id`, e usa a versão da
conversa para eleger um vencedor. Reivindicações perdedoras recebem conflito/not-found. Qualquer
atribuição posterior encerra todos os alertas dessa conversa e envia `notification.changed` para
os tópicos individuais de notificações. “Ler todas” e “Marcar como lida” preservam ofertas ainda
disponíveis. Um marcador de atividade impede que inbound subsequente reatribua ao BOT uma conversa
já encaminhada à fila.

Sessões BOT sem nova mensagem de entrada por 20 minutos recebem um aviso único; aos 30 minutos, o backend
envia uma mensagem de encerramento e muda o status para `CANCELLED`. Uma nova entrada reinicia o prazo.
Ao encerrar por confirmação ou inatividade, `botSessionEndedAt` aparece no DTO; no mesmo CAS a chave
determinística do canal/remetente é arquivada. Uma entrada seguinte cria outro `conversationId` e mantém o
histórico separado. A reabertura de conversas humanas legadas segue igual. `CANCELLED` é terminal e fica
fora das filas abertas. A interface agrupa esses atendimentos em **Encerrados** e deriva quem deve
responder dentro de **Em atendimento** pela direção da última mensagem.
O mesmo retorno concluído recebe `204` em caso de repetição. Um identificador sem pendência recebe
`410`.

Se o OpenBot não devolver o resultado em 25 segundos, o backend usa os trechos da FAQ para
responder ou encaminha para um humano. Sem webhook configurado, o backend usa essa resposta local
diretamente. Pedido explícito por uma pessoa e perguntas sem cobertura podem ser transferidos
sem gerar uma resposta livre. As solicitações pendentes ficam em memória e são perdidas no restart.
O webhook depende de o OpenBot estar em execução. O perfil `dev,legacy` com canal `MOCK` permite
validar mensagens e transferências sem envio pelo WhatsApp.

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
npm run build:backend
npm run lint
npm run build
npm run test
```

Os testes automatizados usam fixtures/mocks e não substituem o teste real. A validação real
começa quando uma pessoa escaneia o QR Code e envia uma mensagem de outro número.
