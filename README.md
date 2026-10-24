# WXN — Atendimento WhatsApp

Chat web com gestão de atendimentos, Java 21/Spring Boot 3.5.0, AngularJS 1.8.3/Vite/Tailwind, PostgreSQL 17 e MongoDB 8. Consultas e envio usam REST; atualizações usam Spring WebSocket/STOMP. WhatsAppProvider seleciona Mock ou Twilio pelo canal original da conversa. Não há login ou IA nesta etapa.

## Entrega: visualizar fila de conversas do chatbot

A persistência e os serviços de consulta da fila estão implementados. A avaliação abaixo considera o enunciado da entrega; critérios adicionais de aceite, como prioridade ou ordem FIFO, não foram fornecidos. A ordenação atual é por atividade mais recente, e a lista inclui conversas da empresa, com filtros opcionais.

| Responsabilidade | Implementação |
| --- | --- |
| Persistir conversas | `Conversation`, coleção MongoDB `conversations`, `_id` nativo e `companyId` |
| Acesso aos dados | `ConversationRepository` e fragmento `ConversationOperationsImpl`, com `MongoTemplate` para filtros e atualizações condicionais |
| Consultar a fila | `ConversationQueryService`: valida empresa, paginação, responsável, limites da busca e filtros incompatíveis |
| Filtrar e ordenar | Status, responsável, sem responsável, nome, telefone e identificador; `updatedAt DESC, _id DESC` |
| Paginar | `ConversationPage` com `items`, `nextPage` e `hasMore`; padrão de 30 itens, máximo de 200 |
| Normalizar respostas | `ConversationMapper` separa o documento persistido de `NormalizedConversation`; status legado `OPEN` é interpretado conforme o responsável |
| Contar não lidas | `ConversationReadService` e `ConversationReadRepository`, por membro HUMAN ativo da empresa, com agregação MongoDB |
| Expor a consulta | `ConversationController` delega aos services; não acessa repositories diretamente |
| Restringir empresa | Consultas incluem `companyId`; o acesso HTTP de desenvolvimento valida chave e empresa configuradas no servidor |

Exemplo: `GET /api/companies/1/conversations?paged=true&status=PENDING&unassigned=true&page=0&size=30`, com o header `X-Dev-Api-Key`. Sem `readerMemberId`, a resposta não calcula a contagem de não lidas. `assignedMemberId` pode identificar HUMAN ou BOT; não há filtro específico por tipo de membro.

A implementação segue a separação Controller → Service → Repository, com DTOs/mappers, Flyway para o PostgreSQL e índices MongoDB definidos em `MongoConfiguration`. O acesso por empresa nesta etapa é um contexto temporário de desenvolvimento, ainda sem autenticação de usuário.

Correções realizadas após a revisão:

- A busca ignora a condição telefônica quando a normalização resulta em vazio, como em `()` ou `-`. Nome e identificadores continuam sendo pesquisados literalmente. A mesma regra é aplicada ao filtro local do frontend.
- O estado de leitura mantém o mapa e as conversas sincronizados. Carregar outra página ou receber uma resposta iniciada antes da confirmação de leitura não restaura a contagem antiga. Respostas novas ainda podem atualizar a contagem quando chegam mensagens.
- Testes de regressão cobrem a construção da consulta de busca, telefone formatado, filtro local e preservação das contagens ao paginar e receber respostas antigas.

A validação integrada da fila foi executada com PostgreSQL e MongoDB reais em containers isolados. Os cenários cobrem filtros, ordenação com desempate por ID, paginação, busca, estados legados, isolamento entre empresas, transições HUMAN/BOT, concorrência e leitura persistida. A matriz de testes e as instruções de reprodução estão em [Validação da fila](docs/queue-verification.md).

## Portas e execução

| Serviço | Acesso pelo host |
| --- | --- |
| Backend | http://localhost:4001 |
| Frontend | http://localhost:4002 |
| WebSocket | ws://localhost:4001/ws |
| PostgreSQL | localhost:4003 (5432 dentro do container) |
| MongoDB | localhost:4004 (27017 dentro do container) |

```bash
cp .env.example .env
# Configure credenciais locais e uma DEV_API_KEY aleatória de pelo menos 32 caracteres.
# VITE_DEV_API_KEY deve conter a mesma chave para o navegador de desenvolvimento.
chmod 600 .env
docker compose up -d
set -a
source .env
set +a
mvn spring-boot:run
```

Em outro terminal:

```bash
cd frontend
npm install
npm run dev
```

Vite lê o ambiente da raiz do projeto e usa `strictPort`. O backend não lê `.env` automaticamente: exporte as variáveis. A página inicial é o chat. Para mudanças no ambiente, reinicie o processo correspondente.

Variáveis principais: `BACKEND_PORT`, `FRONTEND_PORT`, `FRONTEND_ORIGIN`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`, `POSTGRES_URL`, `MONGO_DB`, `MONGO_PORT`, `MONGODB_URI`, `SPRING_PROFILES_ACTIVE`, `DEV_API_KEY`, `DEV_COMPANY_ID`, `VITE_API_BASE_URL`, `VITE_WS_URL`, `VITE_DEV_COMPANY_ID`, `VITE_DEV_API_KEY`.

`POSTGRES_VOLUME` e `MONGO_VOLUME` permitem reutilizar volumes existentes. Se ambos já existirem, configure `DATABASE_VOLUMES_EXTERNAL=true`. Não monte o mesmo volume simultaneamente em dois bancos. Na configuração local desta entrega, os volumes anteriores `wxn-whatsapp-it_postgres_data` e `wxn-whatsapp-it_mongo_data` foram reutilizados pelos novos containers WXN nas portas 4003/4004; os containers antigos foram preservados. Não apague volumes para ajustar portas.

## Contexto temporário e segurança

O contexto de desenvolvimento usa empresa 1, configurada por ambiente. A chave HTTP `X-Dev-Api-Key` continua obrigatória; URLs de outra empresa são recusadas pelo backend. O Mock também valida a empresa do corpo JSON. `companyId` nunca substitui autorização.

STOMP autentica no frame CONNECT com `X-Dev-Api-Key` e `companyId`, pois o WebSocket nativo do navegador não permite headers HTTP personalizados. O endpoint aceita a origem configurada (`http://localhost:4002`). SUBSCRIBE permite somente `/topic/companies/<empresa permitida>/conversations`; destinos de outra empresa, curingas e publicação SEND são rejeitados. Prefixos configurados: `/app`, `/topic`, `/queue`. Não há envio de mensagens pela rede via EventBusService: o envio continua exclusivamente pelo REST.

Rotas internas e WebSocket só ficam disponíveis no perfil `dev`, sem `prod` ativo. A chave exposta no navegador não é autenticação real nem proteção suficiente para produção. O atendente de desenvolvimento pode ser escolhido entre HUMAN ativos existentes, no seletor da sidebar. Não são criados membros silenciosamente pela interface. Antes de produção, implementar autenticação e derivar empresa/membro do usuário autorizado.

## Arquitetura

Backend: controllers validam contratos; services aplicam regras; repositories concentram persistência e atualizações atômicas; mappers separam BSON/JPA dos DTOs; adapters encapsulam o SDK. `ConversationEventPublisher` gera envelopes canônicos e a ponte de infraestrutura STOMP usa `SimpMessagingTemplate`. Services de mensagens/conversas não importam SDK Twilio.

Frontend organizado em `core`, `features` e `shared`:

- `core`: ambiente, ApiClientService/interceptor, EventBusService, constantes e SocketService.
- `chats`: consultas, estado da lista, ações de atendimento, leitura e histórico operacional em services separados; controllers coordenam essas responsabilidades. Diretivas encapsulam sidebar, cabeçalho, detalhes e histórico.
- `messages`: MessagesService encapsula REST; MessageHistoryService controla cursores/reconciliação, MessageSendingService controla tentativas e MessageReadContextService controla leitura. MessagesController coordena eventos; diretivas apresentam histórico, bolhas e composição.
- `users`, `members`, `channels`: services de seus domínios; DevMemberContextService centraliza o atendente HUMAN.
- `shared`: modal reutilizável com controle de foco/Escape, loading, estados vazios e estilos globais Tailwind. AsyncState padroniza IDLE/LOADING/SUCCESS/ERROR.

Templates HTML ficam separados dos arquivos JavaScript. Diretivas não fazem HTTP. Listeners são removidos no ciclo de destruição. O SocketService mantém uma assinatura, reconecta com backoff e traduz eventos do backend para o EventBusService, integrado ao digest AngularJS. Uma confirmação de assinatura, emitida após registro pelo broker simples, precede a reconciliação REST. IDs internos reconciliam HTTP e STOMP sem criar bolhas duplicadas. Reconexão recupera também intervalos maiores que uma página.

Os frames do Figma não puderam ser acessados nesta entrega. O frontend existente foi preservado como referência visual, incluindo cores, espaçamentos e responsividade.

## Gestão de atendimentos

| Estado | Significado |
| --- | --- |
| PENDING | Sem responsável, aguardando atendimento |
| IN_PROGRESS | Atribuído a um HUMAN ou BOT ativo da empresa |
| CLOSED | Atendimento encerrado |

Documentos legados com status `OPEN` são interpretados conforme a atribuição: PENDING sem responsável, IN_PROGRESS com responsável. Não há atualização destrutiva dos documentos nem alteração de Flyway V1–V5.

Atribuição exige membro da mesma empresa, ativo e não excluído; HUMAN também precisa de usuário interno válido. Transferência aceita todas as combinações HUMAN/BOT. A conversa mantém um responsável principal e o canal original. BOT é apenas um membro nesta etapa, sem resposta automática.

Assumir exige HUMAN ativo e conversa sem outro responsável. Atribuição não substitui um responsável existente; nesse caso, utilize transferência. Assumir, atribuição, transferência, encerramento e reabertura recebem `expectedVersion`. Atualizações condicionais no Mongo retornam 409 diante de concorrência. Repetir uma ação que já representa o estado atual não cria outro log ou evento. O encerramento registra `closedAt` e mantém o responsável histórico; a reabertura manual restaura IN_PROGRESS se o responsável continua válido, ou PENDING caso contrário.

Nova mensagem recebida em conversa CLOSED preserva o histórico, reabre automaticamente como PENDING, limpa responsável/closedAt e registra CONVERSATION_AUTO_REOPENED. O envio de nova resposta exige reabrir um atendimento encerrado. Nenhuma mudança de atendimento é enviada como mensagem WhatsApp.

A sidebar permite Todas, Minhas, Não atribuídas, Pendentes, Em atendimento e Finalizadas. Filtros e pesquisa por nome, telefone ou identificador são executados no backend. A pesquisa usa debounce; a lista carrega 30 conversas por página, com carregamento incremental. Eventos atualizam a presença no filtro e a ordem de atividade. O cabeçalho permite assumir, atribuir, transferir com motivo, encerrar/reabrir com confirmação e consultar detalhes/histórico. O histórico mostra os eventos operacionais em ordem cronológica junto das mensagens.

## MongoDB e leitura

Coleções existentes `conversations`, `messages` e `message_logs` foram preservadas. Campos complementares: `displayName`, `lastMessageAt`, `lastMessageId`, `closedAt`, `version` e `pendingActivities`, preservando `lastMessagePreview`. Revisões legadas são inicializadas sem sobrescrever documentos existentes. `updatedAt` acompanha atividades; `lastMessageAt` acompanha mensagens.

Novas coleções:

- `conversation_events`: empresa, conversa, tipo, ator, membro anterior/novo, motivo e timestamp. Chave única impede duplicidade por revisão/evento.
- `conversation_reads`: estado por empresa, conversa e membro HUMAN, com fronteira de leitura por timestamp e ID de mensagem. Índice único desse escopo.

O contador inclui somente INBOUND posteriores à fronteira do membro. Mensagens enviadas não contam. Abrir/visualizar as mensagens recentes marca leitura; enquanto o usuário lê mensagens antigas, novas mensagens recebem indicação sem forçar scroll ou marcar o trecho novo como visto. A leitura não modifica delivery status e não envia read receipt ao WhatsApp. Contagens da lista usam consulta agregada, sem uma consulta por conversa. Mudanças de leitura sincronizam as abas do mesmo membro.

Mensagens são paginadas por cursor de timestamp + ObjectId, com índices correspondentes. O frontend carrega as últimas 30, mantém ordem cronológica e carrega anteriores pelo botão ou scroll no topo, preservando a posição. Consulta antiga sem `limit`/`before` continua retornando a lista paginada por `page`/`size`, para compatibilidade com clientes anteriores.

## Endpoints

Todos os endpoints internos exigem o header de desenvolvimento.

| Método | Rota |
| --- | --- |
| POST / GET | `/api/companies` / `/api/companies/{id}` |
| POST, GET | `/api/companies/{companyId}/users` |
| POST, GET | `/api/companies/{companyId}/members` |
| GET | `/api/companies/{companyId}/members/{memberId}` |
| POST, GET | `/api/companies/{companyId}/whatsapp/channels` |
| GET | `/api/companies/{companyId}/conversations` |
| GET | `/api/companies/{companyId}/conversations/{id}` |
| PATCH | `/api/companies/{companyId}/conversations/{id}/assume` |
| PATCH | `/api/companies/{companyId}/conversations/{id}/assign` |
| PATCH | `/api/companies/{companyId}/conversations/{id}/transfer` |
| PATCH | `/api/companies/{companyId}/conversations/{id}/close` |
| PATCH | `/api/companies/{companyId}/conversations/{id}/reopen` |
| GET | `/api/companies/{companyId}/conversations/{id}/events` |
| POST | `/api/companies/{companyId}/conversations/{id}/read` |
| GET, POST | `/api/companies/{companyId}/conversations/{id}/messages` |
| POST | `/api/dev/whatsapp/mock/inbound` |

Listagem de conversas aceita `status`, `assignedMemberId`, `unassigned`, `readerMemberId`, `search`, `page` e `size` (até 200). Com `paged=true`, retorna `{items,nextPage,hasMore}`; sem esse parâmetro mantém o contrato anterior em lista. `readerMemberId` deve identificar HUMAN ativo da empresa. Exemplos: `?status=IN_PROGRESS`, `?assignedMemberId=1&readerMemberId=1`.

Atribuir/transferir:

```json
{"memberId":2,"actorMemberId":1,"expectedVersion":0,"reason":"Encaminhado ao comercial"}
```

Encerrar/reabrir:

```json
{"actorMemberId":1,"expectedVersion":1,"reason":"Solicitação resolvida"}
```

Marcar leitura até uma mensagem realmente existente nessa conversa:

```json
{"memberId":1,"throughMessageId":"<id interno da mensagem>"}
```

Cursor de mensagens: `GET .../messages?limit=30`, depois `GET .../messages?limit=30&before=<nextCursor>`. Resposta:

```json
{"items":[],"nextCursor":null,"hasMore":false}
```

Enviar texto:

```json
{"channelId":1,"memberId":1,"content":"Como posso ajudar?"}
```

HTTP 201 indica a criação da tentativa, não confirmação de entrega. Mock retorna `SIMULATED` e `simulated: true`; Twilio retorna o estado normalizado, aguardando callbacks para confirmar entrega. Erros mantêm a tentativa e código sanitizado em logs.

## Eventos em tempo real

Envelope comum: `eventId`, `type`, `companyId`, `conversationId`, `occurredAt`, `payload`. Payloads são DTOs, sem entidades JPA ou documentos Mongo.

Eventos backend: `conversation.created`, `conversation.updated`, `message.received`, `message.sent`, `message.status.updated`, `conversation.assigned`, `conversation.transferred`, `conversation.closed`, `conversation.reopened`, `conversation.read.updated`.

Eventos AngularJS usam nomes próprios, separados por domínio: `wxn:chat:created`, `wxn:chat:updated`, `wxn:chat:selected`, `wxn:chat:assigned`, `wxn:chat:transferred`, `wxn:chat:closed`, `wxn:chat:reopened`, `wxn:chat:read-updated`, `wxn:message:received`, `wxn:message:sent`, `wxn:message:status-updated`, `wxn:message:visible`, `wxn:member:updated` e eventos de conexão `wxn:app:*`. `wxn:message:send-requested` é comando local, não evento do backend.

## Exemplos locais e Postman

Os exemplos solicitados foram criados apenas na empresa local 1. O script é manual, não roda no startup nem em produção:

```bash
node scripts/seed-chat-examples.mjs
```

| Telefone fictício | Exemplo |
| --- | --- |
| +5581991000001 | Pendente e não lida |
| +5581991000002 | Atribuído a HUMAN e lido pelo atendente principal |
| +5581991000003 | Atribuído ao Bot Comercial |
| +5581991000004 | Histórico de transferências entre HUMAN/HUMAN, HUMAN/BOT, BOT/BOT e BOT/HUMAN |
| +5581991000005 | Encerrado |
| +5581991000006 | Reaberto manualmente |
| +5581991000007 | Reaberto automaticamente por nova mensagem |
| +5581991000008 | Histórico de 36 mensagens para paginação |

O script reutiliza cadastros compatíveis e preserva alterações manuais ao ser executado novamente. Os IDs externos dos recebimentos são estáveis. Nenhuma mensagem desses exemplos foi enviada ao WhatsApp real.

Para simular recebimento pelo Postman, use `http://localhost:4001/api/dev/whatsapp/mock/inbound`, com `X-Dev-Api-Key`, Content-Type JSON e IDs reais de empresa/canal:

```json
{"companyId":1,"channelId":1,"externalMessageId":"manual-001","senderPhone":"+5581991000001","senderName":"Contato de exemplo","content":"Nova mensagem de exemplo"}
```

Use outro `externalMessageId` para uma mensagem nova. Repetir o ID mantém a idempotência. Abra duas abas em http://localhost:4002 para acompanhar atualizações; escolha o mesmo HUMAN para observar a leitura compartilhada. A coleção anterior permanece em `postman/`, mas seu fluxo de criação de outra empresa não pode acessar esse novo tenant no contexto temporário restrito à empresa 1; utilize os IDs já existentes para os testes manuais atuais.

## Twilio Sandbox

Uma conta e um canal real são mapeados explicitamente por ambiente: `TWILIO_COMPANY_ID`, `TWILIO_CHANNEL_ID`, `TWILIO_CHANNEL_KEY`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_PHONE_NUMBER`, `TWILIO_PUBLIC_BASE_URL`.

Ative o Sandbox na Console Twilio e faça o telefone participar enviando o código `join ...` informado. Cadastre o canal TWILIO e configure seus IDs e o mesmo telefone E.164, sem `whatsapp:`. Execute `ngrok http 4001`, configure a origem HTTPS sem barra final, reexporte o ambiente e reinicie o backend.

Webhooks POST:

- `https://<dominio>/api/webhooks/whatsapp/twilio/<channelKey>`: When a message comes in.
- `https://<dominio>/api/webhooks/whatsapp/twilio/<channelKey>/status`: callback, também enviado pelo adapter.

A assinatura é validada com o SDK oficial, URL configurada e todos os parâmetros form-urlencoded; `AccountSid` e canal são conferidos. O remetente compartilhado do Sandbox não determina sozinho a empresa. Ao mudar o túnel, atualize Console e ambiente. Esta versão não usa query strings nesses webhooks.

Texto livre depende da janela de 24 horas após mensagem do cliente; fora dela são necessários templates aprovados, ainda não implementados. Grupos não são suportados. Recebimento de mídia guarda texto/quantidade, sem download ou envio de anexos. Referências oficiais: [Sandbox](https://www.twilio.com/docs/whatsapp/sandbox), [assinaturas](https://www.twilio.com/docs/usage/webhooks/webhooks-security).

## Consistência e verificações

Transições de atendimento usam compare-and-set por versão. Estado e evento pendente são gravados atomicamente no mesmo documento de conversa; o service projeta o evento na coleção `conversation_events`, com chave única, e confirma sua remoção da lista pendente. Em caso de interrupção, consultas/novas operações retomam a projeção. Mensagens recebidas possuem marcador interno `inboundCompleted`: repetir um webhook interrompido retoma a atualização da conversa e os logs, sem inserir outra mensagem. Logs novos possuem chave idempotente; índices parciais preservam registros legados.

`CanonicalEventPayload` define contratos de eventos, `ConversationTransitionService` centraliza persistência/auditoria/publicação, e `MessageStatusTransitions` centraliza progressão de entrega. O resolver registra adapters por provedor; capacidades delimitam envio individual de texto. Normalização de status é contrato separado, utilizado pelo adapter que recebe callbacks.

Comandos de verificação:

```bash
mvn test
cd frontend
npm test
npm run build
# Na raiz, com .env configurado:
docker compose config --quiet
git diff --check
```

Na validação completa de 23/10/2026, passaram 42 testes Java, incluindo os 14 cenários de `ConversationQueueIntegrationTest` e o fluxo de `LocalFlowIntegrationTest`, sem falhas ou testes ignorados. Também passaram os 7 testes do frontend, o empacotamento Spring Boot, o build Vite, a configuração Compose e `git diff --check`. Para os testes Java, o Mockito foi carregado explicitamente como agente, pois sua anexação dinâmica à JVM falhou no ambiente restrito.

Para reproduzir a execução com agente, substitua o caminho pelo JAR local da versão de Mockito resolvida pelo Maven:

```bash
mvn -DargLine="-javaagent:/caminho/mockito-core-<versao>.jar" test
```

Para executar a verificação completa, com `.env` configurado e Docker disponível, utilize na raiz:

```bash
bash scripts/verify-queue.sh
# Se a JVM exigir agente explícito:
MOCKITO_AGENT_JAR=/caminho/mockito-core-<versao>.jar bash scripts/verify-queue.sh
```

O script inicia bancos isolados nas portas 14003/14004, ativa `WXN_INTEGRATION_TEST=true`, executa `mvn clean verify`, testes/build do frontend e conferência do diff, e para os containers ao terminar. Os volumes `wxn-queue-review_postgres_data` e `wxn-queue-review_mongo_data` preservam somente os dados de teste. Execuções diretas dos testes de integração gravam fixtures nos bancos configurados; prefira o script para evitar o banco de desenvolvimento. Não foram feitas chamadas reais à Twilio.

Não existe transação distribuída entre PostgreSQL e MongoDB. A entrega STOMP não é durável: reconexão e consultas REST reconciliam o estado persistido. Envios REST não possuem chave de idempotência do cliente; uma tentativa Twilio com resultado remoto desconhecido não deve ser repetida automaticamente. A integração real depende das credenciais e telefones habilitados no Sandbox e permanece sem validação nesta entrega.

Antes de produção, implementar login e autorização por tenant/membro. BOT permanece sem automação; anexos, templates e grupos reais não fazem parte desta entrega. Nenhum commit ou push foi realizado.

## Equipe

- João Pedro dos Santos Sousa - `jpss2@cesar.school`
- Luis Filipe Alves Silva Santos - `lfass@cesar.school`
- Lucas Samuel Pereira Alves - `lspa@cesar.school`
- Matheus Costa da Rocha -`mcr@cesar.school`
- Miguel josé França de Andrade - `mjfa@cesar.school `
- Pedro Henrique Ramos da Silva - `phrs@cesar.school`
- Vitória Gabrielle Janeiro Duran - `vgjda2@cesar.school`
