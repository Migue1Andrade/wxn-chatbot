> Registro histórico da refatoração estrutural anterior à V4. Cadastro e autenticação atuais:
> [contas de usuário](user-accounts.md). Não use este relatório como contrato atual de usuários.

**Relatório da refatoração WXN para organização única — 24/10/2026**

A refatoração foi implementada e compilada. O reset físico dos bancos **não foi executado**.
As etapas dependentes de bancos e serviços em execução permanecem pendentes pelas restrições descritas abaixo.
Nenhum commit ou push foi criado. `AGENTS.md` não foi modificado. Nenhuma suíte de testes nova foi criada;
os testes existentes foram adaptados aos contratos atuais, removendo cenários da arquitetura abandonada.

**A. Bancos resetados**

Nenhum. A configuração Compose e as variáveis locais foram inspecionadas sem expor credenciais:

| Recurso    | Configuração local encontrada                                                          | Resultado |
| ---------- | -------------------------------------------------------------------------------------- | --------- |
| PostgreSQL | database `wxn_whatsapp_it`, porta 4003, volume externo `wxn-whatsapp-it_postgres_data` | Intacto   |
| MongoDB    | database `wxn_whatsapp_it`, porta 4004, volume externo `wxn-whatsapp-it_mongo_data`    | Intacto   |

`DATABASE_VOLUMES_EXTERNAL=true` permite reutilização de volumes. `docker ps` e `docker inspect`
foram negados pelo ambiente com `operation not permitted` no socket do Docker. Não foi possível
comprovar uso exclusivo nem consultar os containers e seus vínculos. Nenhuma exclusão foi tentada.
A validação estática `docker compose config --quiet` passou.

Não execute remoção global de recursos Docker. Antes de resetar esses databases, é necessário inspecionar
os containers, labels Compose, mounts e todos os consumidores dos volumes, comprovando exclusividade WXN.
O reset deve atingir somente os databases exclusivos identificados, sem excluir volumes compartilhados.

**B. Novas migrations**

1. `V1__create_users_and_members.sql`: usuários e membros, relacionamento funcional entre eles.
2. `V2__create_departments.sql`: setores e associação N:N com membros.
3. `V3__create_whatsapp_channels.sql`: canais MOCK/TWILIO.

O histórico anterior foi removido fisicamente dos fontes. Um `mvn clean -DskipTests package` removeu
classes e migrations antigas do artefato. O JAR foi inspecionado: contém somente as três migrations novas
e nenhuma classe do módulo removido. Essas migrations **não foram aplicadas** aos bancos locais.
O histórico antigo é incompatível: não execute Flyway repair para contornar a necessidade de reset.

**C. Estrutura PostgreSQL final definida nos fontes**

| Tabela               | Estrutura e integridade                                                                                                                        |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `users`              | ID identity, nome, telefone opcional E.164 único, timestamps e exclusão lógica                                                                 |
| `members`            | ID identity, FK `user_id`, nome, tipo HUMAN/BOT, status, timestamps e exclusão lógica; HUMAN exige usuário; usuário HUMAN ativo não se duplica |
| `departments`        | ID identity, nome obrigatório, cor hexadecimal e exclusão lógica                                                                               |
| `member_departments` | FKs para membros/setores, PK `(member_id, department_id)`, índice por setor, sem cascata de exclusão                                           |
| `whatsapp_channels`  | ID identity, nome, provider, telefone E.164 opcional, ID externo, status, timestamps e exclusão lógica                                         |

Não há entidade organizacional fictícia. Os IDs continuam sendo gerados pelo PostgreSQL.
O Hibernate permanece com `ddl-auto=validate`. A exclusão de setores vinculados continua bloqueada pela aplicação,
inclusive se os atendentes estiverem inativos. Cadastro e edição de HUMAN mantêm os vínculos N:N em transação.

**D. Estrutura MongoDB final definida nos fontes**

| Collection            | Identificação e índices                                                                                                                                       |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `conversations`       | `_id` nativo; índice operacional por status/responsável/data; unicidade `(channelId, conversationKey)`                                                        |
| `messages`            | `_id` nativo, conversa/canal/provider; índices por conversa/data/ID e direção; unicidade parcial `(channelId, externalMessageId)` para IDs externos presentes |
| `message_logs`        | `_id` nativo, mensagem/canal/ID externo; `eventKey` único                                                                                                     |
| `conversation_events` | `_id` nativo, conversa/atores/transição/motivo/data; `eventKey` único e índice por conversa/data/ID                                                           |
| `conversation_reads`  | `_id` nativo, conversa/membro/limite lido; unicidade `(conversationId, memberId)`                                                                             |

O ID externo pode estar ausente em mensagens de saída enquanto o provider ainda não respondeu; por isso
sua unicidade continua parcial. Nenhum índice usa um identificador organizacional. Idempotência de entrada
e callbacks preserva o canal; atribuições, auditoria, status e limites de leitura foram mantidos.
Os índices são criados no startup, mas sua criação real **não foi executada** nesta sessão.

**E. Arquivos excluídos nesta refatoração**

- `docs/whatsapp-delivery.md`
- `src/main/java/com/wxn/chatbot/company/controller/CompanyController.java`
- `src/main/java/com/wxn/chatbot/company/dto/CompanyDtos.java`
- `src/main/java/com/wxn/chatbot/company/entity/Company.java`
- `src/main/java/com/wxn/chatbot/company/mapper/CompanyMapper.java`
- `src/main/java/com/wxn/chatbot/company/repository/CompanyRepository.java`
- `src/main/java/com/wxn/chatbot/company/service/CompanyService.java`
- `src/main/resources/db/migration/V1__create_companies_table.sql`
- `src/main/resources/db/migration/V2__create_users_table.sql`
- `src/main/resources/db/migration/V3__add_user_phone.sql`
- `src/main/resources/db/migration/V4__create_members.sql`
- `src/main/resources/db/migration/V5__create_whatsapp_channels.sql`
- `src/main/resources/db/migration/V6__create_departments_and_member_departments.sql`

Também foram removidos campos, validações, filtros e assinaturas organizacionais dos arquivos ativos;
foram removidos métodos de consulta sem consumidor, inicialização corretiva de revisão antiga e construtores
de compatibilidade sem utilidade. Não restaram stubs do módulo excluído.

**F. Arquivos criados nesta refatoração**

- `docs/single-organization-report.md`
- `src/main/resources/db/migration/V1__create_users_and_members.sql`
- `src/main/resources/db/migration/V2__create_departments.sql`
- `src/main/resources/db/migration/V3__create_whatsapp_channels.sql`

**G. Arquivos modificados nesta refatoração**

A lista compara os fontes com o estado encontrado no início desta execução. As alterações anteriores da
área de trabalho foram preservadas. As configurações Compose foram revisadas e mantidas, pois já não
continham dependências organizacionais e as portas existentes permanecem adequadas.

- `.env (arquivo local não versionado; somente variáveis obsoletas removidas)`
- `.env.example`
- `README.md`
- `docs/code-style.md`
- `docs/departments.md`
- `docs/queue-verification.md`
- `frontend/src/core/config/environment.js`
- `frontend/src/core/http/api-client.service.js`
- `frontend/src/core/socket/socket.service.js`
- `frontend/src/features/members/dev-member-context.service.js`
- `postman/WXN-WhatsApp.postman_collection.json`
- `scripts/seed-chat-examples.mjs`
- `src/main/java/com/wxn/chatbot/config/DevelopmentAccess.java`
- `src/main/java/com/wxn/chatbot/config/DevelopmentApiGuard.java`
- `src/main/java/com/wxn/chatbot/config/MongoConfiguration.java`
- `src/main/java/com/wxn/chatbot/conversation/controller/ConversationController.java`
- `src/main/java/com/wxn/chatbot/conversation/document/Conversation.java`
- `src/main/java/com/wxn/chatbot/conversation/document/ConversationActivity.java`
- `src/main/java/com/wxn/chatbot/conversation/document/ConversationRead.java`
- `src/main/java/com/wxn/chatbot/conversation/dto/ConversationManagementDtos.java`
- `src/main/java/com/wxn/chatbot/conversation/event/CanonicalEventPayload.java`
- `src/main/java/com/wxn/chatbot/conversation/event/ConversationEvent.java`
- `src/main/java/com/wxn/chatbot/conversation/event/ConversationEventPublisher.java`
- `src/main/java/com/wxn/chatbot/conversation/event/LocalConversationEventPublisher.java`
- `src/main/java/com/wxn/chatbot/conversation/mapper/ConversationActivityMapper.java`
- `src/main/java/com/wxn/chatbot/conversation/mapper/ConversationMapper.java`
- `src/main/java/com/wxn/chatbot/conversation/repository/ConversationActivityRepository.java`
- `src/main/java/com/wxn/chatbot/conversation/repository/ConversationOperations.java`
- `src/main/java/com/wxn/chatbot/conversation/repository/ConversationOperationsImpl.java`
- `src/main/java/com/wxn/chatbot/conversation/repository/ConversationReadOperations.java`
- `src/main/java/com/wxn/chatbot/conversation/repository/ConversationReadOperationsImpl.java`
- `src/main/java/com/wxn/chatbot/conversation/repository/ConversationReadRepository.java`
- `src/main/java/com/wxn/chatbot/conversation/repository/ConversationRepository.java`
- `src/main/java/com/wxn/chatbot/conversation/service/ConversationActivityService.java`
- `src/main/java/com/wxn/chatbot/conversation/service/ConversationManagementService.java`
- `src/main/java/com/wxn/chatbot/conversation/service/ConversationQueryService.java`
- `src/main/java/com/wxn/chatbot/conversation/service/ConversationReadService.java`
- `src/main/java/com/wxn/chatbot/conversation/service/ConversationService.java`
- `src/main/java/com/wxn/chatbot/conversation/service/ConversationTransitionService.java`
- `src/main/java/com/wxn/chatbot/department/controller/DepartmentController.java`
- `src/main/java/com/wxn/chatbot/department/entity/Department.java`
- `src/main/java/com/wxn/chatbot/department/repository/DepartmentRepository.java`
- `src/main/java/com/wxn/chatbot/department/service/DepartmentService.java`
- `src/main/java/com/wxn/chatbot/member/controller/MemberController.java`
- `src/main/java/com/wxn/chatbot/member/dto/MemberDtos.java`
- `src/main/java/com/wxn/chatbot/member/entity/Member.java`
- `src/main/java/com/wxn/chatbot/member/mapper/MemberMapper.java`
- `src/main/java/com/wxn/chatbot/member/repository/MemberRepository.java`
- `src/main/java/com/wxn/chatbot/member/service/MemberService.java`
- `src/main/java/com/wxn/chatbot/message/controller/MessageController.java`
- `src/main/java/com/wxn/chatbot/message/document/Message.java`
- `src/main/java/com/wxn/chatbot/message/mapper/MessageMapper.java`
- `src/main/java/com/wxn/chatbot/message/repository/MessageOperations.java`
- `src/main/java/com/wxn/chatbot/message/repository/MessageOperationsImpl.java`
- `src/main/java/com/wxn/chatbot/message/repository/MessageRepository.java`
- `src/main/java/com/wxn/chatbot/message/service/MessageService.java`
- `src/main/java/com/wxn/chatbot/messagelog/document/MessageLog.java`
- `src/main/java/com/wxn/chatbot/messagelog/repository/MessageLogRepository.java`
- `src/main/java/com/wxn/chatbot/messagelog/service/MessageLogService.java`
- `src/main/java/com/wxn/chatbot/user/controller/UserController.java`
- `src/main/java/com/wxn/chatbot/user/dto/UserDtos.java`
- `src/main/java/com/wxn/chatbot/user/entity/User.java`
- `src/main/java/com/wxn/chatbot/user/mapper/UserMapper.java`
- `src/main/java/com/wxn/chatbot/user/repository/UserRepository.java`
- `src/main/java/com/wxn/chatbot/user/service/UserService.java`
- `src/main/java/com/wxn/chatbot/websocket/DevStompPrincipal.java`
- `src/main/java/com/wxn/chatbot/websocket/DevelopmentStompInterceptor.java`
- `src/main/java/com/wxn/chatbot/websocket/StompConversationEventBridge.java`
- `src/main/java/com/wxn/chatbot/websocket/StompDestinations.java`
- `src/main/java/com/wxn/chatbot/whatsapp/config/TwilioProperties.java`
- `src/main/java/com/wxn/chatbot/whatsapp/controller/ChannelController.java`
- `src/main/java/com/wxn/chatbot/whatsapp/dto/ChannelDtos.java`
- `src/main/java/com/wxn/chatbot/whatsapp/dto/MockInboundRequest.java`
- `src/main/java/com/wxn/chatbot/whatsapp/entity/WhatsAppChannel.java`
- `src/main/java/com/wxn/chatbot/whatsapp/mapper/ChannelMapper.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/NormalizedConversation.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/NormalizedMessage.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/SendMessageRequest.java`
- `src/main/java/com/wxn/chatbot/whatsapp/provider/TwilioWhatsAppProvider.java`
- `src/main/java/com/wxn/chatbot/whatsapp/repository/WhatsAppChannelRepository.java`
- `src/main/java/com/wxn/chatbot/whatsapp/service/ChannelService.java`
- `src/main/java/com/wxn/chatbot/whatsapp/service/MockIngressService.java`
- `src/main/java/com/wxn/chatbot/whatsapp/service/TwilioIngressService.java`
- `src/main/resources/application.yml`
- `src/test/java/com/wxn/chatbot/ConversationQueueIntegrationTest.java`
- `src/test/java/com/wxn/chatbot/LocalFlowIntegrationTest.java`
- `src/test/java/com/wxn/chatbot/config/DevelopmentApiGuardTest.java`
- `src/test/java/com/wxn/chatbot/conversation/repository/ConversationOperationsTest.java`
- `src/test/java/com/wxn/chatbot/conversation/service/ConversationQueryServiceTest.java`
- `src/test/java/com/wxn/chatbot/member/MemberServiceTest.java`
- `src/test/java/com/wxn/chatbot/message/MessageServiceTest.java`
- `src/test/java/com/wxn/chatbot/websocket/ConversationEventsTest.java`
- `src/test/java/com/wxn/chatbot/websocket/DevelopmentStompInterceptorTest.java`
- `src/test/java/com/wxn/chatbot/whatsapp/ProviderTest.java`
- `src/test/java/com/wxn/chatbot/whatsapp/TwilioIngressTest.java`

**H. Endpoints alterados**

O prefixo `/api/companies/{companyId}` foi substituído por `/api`, sem aliases para as rotas antigas.
A API de empresas foi excluída. Os recursos atuais são:

| Métodos       | Recurso                                                                                   |
| ------------- | ----------------------------------------------------------------------------------------- |
| POST, GET     | `/api/users`                                                                              |
| POST, GET     | `/api/members`                                                                            |
| GET, PATCH    | `/api/members/{memberId}`                                                                 |
| POST, GET     | `/api/departments`                                                                        |
| PATCH, DELETE | `/api/departments/{id}`                                                                   |
| POST, GET     | `/api/whatsapp/channels`                                                                  |
| GET           | `/api/conversations`                                                                      |
| GET           | `/api/conversations/{conversationId}`                                                     |
| PATCH         | `/api/conversations/{conversationId}/assume`, `/assign`, `/transfer`, `/close`, `/reopen` |
| GET           | `/api/conversations/{conversationId}/events`                                              |
| POST          | `/api/conversations/{conversationId}/read`                                                |
| GET, POST     | `/api/conversations/{conversationId}/messages`                                            |

Mock continua em `/api/dev/whatsapp/mock/inbound`, agora sem campo de empresa. Webhooks Twilio continuam
em `/api/webhooks/whatsapp/twilio/{channelKey}` e `/status`, preservando assinatura, conta, telefone e canal.
Os DTOs, normalizadores, providers e clientes REST foram atualizados em conjunto. A coleção Postman começa
com cadastro de setor e envia `departmentIds` no cadastro HUMAN. O seed também segue esse contrato.

**I. Eventos STOMP alterados**

A assinatura anterior por empresa foi substituída por `/topic/conversations`. CONNECT usa somente
`X-Dev-Api-Key`; o principal de desenvolvimento identifica a sessão. Envelope atual:
`eventId`, `type`, `conversationId`, `occurredAt`, `payload`. Tipos de eventos foram preservados.
O cliente ignora duplicações por `eventId`. SUBSCRIBE aceita apenas o destino exato, sem curingas;
SEND continua bloqueado e o envio de mensagens permanece via REST. Validações de origem permanecem.

**J. Configurações removidas**

- `DEV_COMPANY_ID` e `VITE_DEV_COMPANY_ID` do exemplo e do ambiente local.
- `TWILIO_COMPANY_ID` do exemplo e do ambiente local.
- Propriedades `wxn.dev.company-id` e `wxn.twilio.company-id`.
- Identificador de empresa no ambiente AngularJS, URLs REST, cabeçalhos CONNECT, destinos STOMP e chave do sessionStorage.
- FKs, índices, constraints e filtros da arquitetura anterior.

A chave de desenvolvimento, os perfis dev/prod, credenciais e associação de conta/canal Twilio foram preservados.
Não foi introduzido login, senha, JWT, sessão de usuário, Spring Security, RBAC ou contexto organizacional fixo.
A busca final não encontrou referências da arquitetura removida no código ativo ou nos documentos operacionais;
as ocorrências neste relatório são intencionais para registrar o que foi excluído. `AGENTS.md` foi preservado.

**K. Funcionalidades e verificações efetivamente executadas**

- `npm run lint:fix`: passou.
- `npm run lint`: passou, incluindo Checkstyle, ESLint, formatação e sintaxe shell.
- `mvn clean -DskipTests package`: passou, incluindo compilação dos testes existentes.
- `npm --prefix frontend run build`: passou (o script de build fica no pacote frontend).
- `git diff --check`: passou; diff, arquivos novos e migrations revisados.
- `docker compose config --quiet`: passou, sem iniciar containers.
- Suítes Java existentes: 24 testes unitários passaram, 15 integrações opt-in não executadas.
  Foi usado o agente Mockito local para evitar o auto-attach bloqueado neste ambiente.
- Testes frontend existentes: 7 passaram.
- Validação offline Hibernate/Spring Data: os quatro mapeamentos JPA e os quatro repositories foram construídos
  e validados sem banco; isso não comprova SQL executado ou funcionamento dos endpoints.
- Inspeção do JAR: somente as três migrations novas; nenhuma classe do módulo excluído.

**L. Pendências reais e bloqueios**

1. Comprovar que os containers, databases e volumes externos identificados são exclusivos da WXN.
2. Resetar exclusivamente esses databases e aplicar as migrations novas; os bancos atuais continuam intactos.
3. Inicializar PostgreSQL/MongoDB e verificar a criação real dos índices MongoDB.
4. Reiniciar backend/frontend e validar REST, WebSocket, Mock, persistência e CRUD de atendentes/setores em execução.

As tentativas reais de startup falharam por restrições do ambiente: backend com
`java.net.SocketException: Operation not permitted` na conexão PostgreSQL; frontend com
`listen EPERM ::1:4002`. Uma tentativa adicional de validar SQL em PostgreSQL temporário, sem tocar os bancos
locais, também foi bloqueada ao criar memória compartilhada (`shmget: Operation not permitted`).
Não foram declarados reset, aplicação das migrations, integração REST/STOMP, envio real Twilio ou persistência
como concluídos. Os testes unitários dos providers e assinaturas passaram; serviços reais não foram exercitados.
