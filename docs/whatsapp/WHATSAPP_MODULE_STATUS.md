# WhatsApp Module Release Candidate — estado de homologação

Data: 31/10/2026. Referência técnica: [operação e arquitetura](README.md).

## Resultado objetivo

| Classificação       | Resultado                                                 | Evidência e pendência                                                                                                                   |
| ------------------- | --------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| MOCK_READY          | PARCIAL — implementação pronta para homologação integrada | Fluxos canônicos implementados, testes isolados executados; cenário HTTP/bancos/navegador completo não pôde ser executado neste sandbox |
| ZAPI_CONTRACT_READY | PRONTO no nível de contrato local coberto                 | Fixtures HTTP validam paths/métodos/headers/JSON/query e erros; não comprovam conta conectada nem elegibilidade real de mensagens       |
| ZAPI_LIVE_VERIFIED  | BLOQUEADO POR CREDENCIAIS/AMBIENTE AUTORIZADO             | Não houve envio, recebimento, pareamento, restart ou disconnect em instância real                                                       |

**Não se declara o checklist completo de aceite homologado.** O candidato técnico precisa da execução integrada com PostgreSQL/MongoDB acessíveis e revisão de UI. O ambiente recusou socket Docker e TCP loopback das portas 4001/4003/4004 com `operation not permitted`; nenhuma tentativa de contornar essa restrição foi feita.

## Diagnóstico inicial

| Recurso inspecionado          | Situação encontrada                                                                                |
| ----------------------------- | -------------------------------------------------------------------------------------------------- |
| WhatsAppChannel / cadastro    | IMPLEMENTADO MAS NÃO VALIDADO integradamente; criação/listagem REST, sem tela administrativa       |
| WhatsAppProvider / resolver   | IMPLEMENTADO E VALIDADO por testes existentes de seleção Mock/Twilio; faltavam novos contratos     |
| MockWhatsAppProvider          | PARCIAL: envio/reply/forward/delete e ingress, sem conexão/cenários/mutações completas             |
| ZApiWhatsAppProvider / client | PARCIAL: send/reply/forward/delete/status, sem QR/edição/reações/leitura; sem testes HTTP próprios |
| TwilioWhatsAppProvider        | IMPLEMENTADO com testes de normalização/assinatura; conta real não validada                        |
| Webhooks / DTOs externos      | PARCIAL: autenticação de segredo e normalização, sem inbox durável/retentativa                     |
| DTOs canônicos                | IMPLEMENTADO; precisavam estado de mutações e contato interno                                      |
| MessageService                | PARCIAL: idempotência/status; riscos de callback cedo, eco ambíguo e tentativa órfã                |
| ConversationService           | PARCIAL: conversa estável/ciclos; mensagem antiga podia reabrir resolução                          |
| Repositories / índices        | IMPLEMENTADO, precisavam índices de inbox/reconciliação/identidades novas                          |
| EventPublisher / STOMP        | IMPLEMENTADO com destinos por perfil e rechecagem outbound; sem evento de conexão                  |
| Inbox services / composer     | IMPLEMENTADO; rascunho/template/reply existentes, novas ações ausentes                             |
| Message actions               | PARCIAL: copiar/reutilizar/reply/forward/excluir; edição/reação/leitura ausentes                   |
| Channel settings              | AUSENTE no frontend                                                                                |
| Auth/permissões               | IMPLEMENTADO em sessão, CSRF, chave dev e interceptors; não é uma nova autenticação de produção    |
| Logs e erros                  | Sanitização existente; faltavam envelopes com resultado operacional e intenções de novas ações     |

Não havia justificativa para uma nova mensageria paralela. As correções estendem os mesmos serviços, coleções e eventos.

## Problemas corrigidos

1. Callback confirmado sem garantia de processamento posterior: envelope persistido antes do ACK, deduplicação e tentativas limitadas.
2. FromMe ambíguo podia ser descartado operacionalmente: falha de correlação permanece visível e reprocessável, sem deduplicação por texto.
3. Delivery anterior à resposta não preservava a correlação de fila: logs de `zaapId` separados e replay após associação.
4. Tentativa PENDING abandonada por interrupção: reconciliador marca UNKNOWN após dois minutos e não reenvia.
5. Mensagem PENDING/FAILED podia encerrar espera da equipe: apenas resposta confirmada/simulada altera esse relógio.
6. Mensagem anterior a resolvedAt podia reabrir atendimento: limite temporal da conclusão aplicado.
7. Operações disputavam atribuição: lease compartilhado, versão de conversa e nova checagem do ator antes de enviar; old subscriptions continuam sendo verificadas por atribuição no outbound.
8. Atribuição não notificava de forma durável: pendência operacional + notificação PostgreSQL idempotente; migration V11.
9. Exclusão retornava conteúdo antigo no DTO: tombstone omite o conteúdo e atualiza prévia da última mensagem; auditoria preservada.
10. Edição/reação/leitura sem contratos: Strategy canônica, intenção persistida, estado confirmado distinto de ACCEPTED/UNKNOWN e UI correspondente.
11. Nome externo sem política de precedência: contato por canal/telefone, ID interno e respeito a nome marcado como personalizado; LID preservado sem inventar número.
12. Diagnóstico e pareamento ausentes: tela administrativa integrada, status temporal, QR/Challenge, política explícita de leitura e simulador Mock.
13. Payload sem limite anterior ao parsing: limite de tamanho e limitação de requisições com estado de IP limitado.
14. Possibilidade de regredir confirmação de mutação com resposta HTTP atrasada: confirmação observada tem precedência sobre ACK tardio.

## Arquitetura final e integração

### Strategy e providers

`WhatsAppProvider` mantém send/reply/forward/delete e acrescenta conexão, instruções de pareamento, mutações e operações administrativas. Cada adapter declara capabilities reais. Defaults recusam operações não suportadas; Twilio não simula recursos novos ausentes.

- MOCK: simulação determinística em dev, usando os mesmos modelos e persistência.
- ZAPI: contratos centralizados em ZApiClient; configuração exclusivamente server-side, host fixo HTTPS e canal explicitamente vinculado.
- TWILIO: adapter/assinaturas anteriores preservados, sem expansão de escopo ou chamada real.

A implementação atual liga uma instância Z-API ao slot de ambiente. Múltiplos canais de providers diferentes são suportados. Outra instância Z-API exige novo slot seguro no adapter; nenhum canal extra reaproveita credenciais de forma implícita.

### Envio, status, fromMe e reconciliação

`operationId` é o clientRequestId já existente. A tentativa precede o provider. UNKNOWN não dispara retry. IDs internos, remotos, fila e resposta ficam separados. Logs de callback antecipado são reaplicados e status não regridem.

FromMe conhecido é deduplicado por canal/ID remoto. Eco API sem associação fica pendente; mensagem do aplicativo vira OUTBOUND com origem externa sem atribuição inventada. Reconciliação manual exige evidência já recebida. O processo não pode identificar com certeza uma tentativa sem ID/fila somente comparando texto/horário; esse limite é intencional.

### Conversas e contatos

Uma conversa individual permanece estável por canal + identidade normalizada. Reabertura conserva ciclos e volta à triagem sem responsável. Contatos novos possuem ID interno em `contacts`, com unicidade canal/telefone. Dados legados são vinculados quando usados; não houve backfill/reset de todo o banco nem criação de uma nova página CRM.

### Autorização e WebSocket

ADMIN gerencia canais/QR/diagnóstico/instância. ATTENDANT continua limitado ao Member HUMAN atribuído. REST verifica sessão, CSRF e atribuição; STOMP valida assinatura e entrega. `/topic/admin/channels` é exclusivo ADMIN. Sinais mínimos de conexão chegam pelo tópico individual aos usuários com conversas atribuídas no canal. Não há broadcast privado para todos os atendentes.

Mensagens internas e notas permanecem nos serviços internos; não chamam WhatsAppProvider. Não foram criadas menções, BOT ou automações inexistentes.

### Frontend

Inbox mantém paginação, rascunhos, templates e citação. Foram adicionados edição, reações/remoção, leitura remota explícita, estado de intenção e indicador de conexão. Conteúdo confirmado não é substituído por um ACK incerto. Copiar continua local; reutilizar não envia.

`/settings/channels` mostra canais, provider, número configurado, capacidades, estado consultado, último callback, falhas/pêndencias, QR/Challenge e operações confirmadas. Não aceita URLs/tokens no navegador. Cadastro de canal existe; edição arbitrária de provider/instância de conversas históricas não foi introduzida.

## API e eventos adicionados

| Contrato WXN                                          | Acesso / finalidade                                                           |
| ----------------------------------------------------- | ----------------------------------------------------------------------------- |
| `GET /api/admin/channels/{id}/diagnostic`             | ADMIN; snapshot real, não é promessa de conexão eterna                        |
| `POST /api/admin/channels/{id}/qr`                    | ADMIN + CSRF; QR transitório ou Challenge                                     |
| `POST /api/admin/channels/{id}/webhooks`              | ADMIN + confirmação; cadastro via Strategy sem payload específico no frontend |
| `POST /api/admin/channels/{id}/operation`             | ADMIN + confirmação; restart/disconnect                                       |
| `POST /api/admin/channels/{id}/remote-read-policy`    | ADMIN; política de leitura remota                                             |
| `POST /api/admin/channels/{id}/reconcile`             | ADMIN; correlação sem reenvio com evidência                                   |
| `GET /api/admin/channels/{id}/webhook-inbox`          | ADMIN; até 50 envelopes pendentes/falhos, sem payload                         |
| `POST /api/admin/channels/{id}/webhook-inbox/retry`   | ADMIN; nova rodada limitada de processamento                                  |
| `POST /api/messages/{id}/operations`                  | acesso à conversa; EDIT_TEXT/SEND_REACTION/REMOVE_REACTION/MARK_READ          |
| `POST /api/dev/whatsapp/mock/scenario`                | ADMIN dev; próximo envio SUCCESS/FAIL/TIMEOUT                                 |
| `POST /api/dev/whatsapp/mock/from-me`                 | ADMIN dev; envio observado fora da WXN                                        |
| `POST /api/dev/whatsapp/mock/api-echo`                | ADMIN dev; eco da API para correlação tardia                                  |
| `POST /api/webhooks/zapi/connected` e `/disconnected` | segredo próprio + instância validada                                          |
| aliases `/delivery` e `/received-delivery`            | compatibilidade com endpoints anteriores                                      |

As rotas de envio/reply/forward/delete/status Mock anteriores foram preservadas. O endpoint legado de administração ZAPI permanece para compatibilidade; novos módulos devem usar os contratos canônicos, não DTOs específicos de provider.

Evento `channel.connection.changed` é novo. Mensagens editadas/reagidas usam o evento canônico de atualização da mensagem; Inbox/Kanban continuam consumindo os eventos existentes. Notificações de atribuição/transferência não concedem acesso à conversa.

## Persistência, migration e índices

Nova migration: `V11__notification_delivery_deduplication.sql`, coluna opcional e índice único parcial para entrega idempotente de notificações. V1–V10 permaneceram intactas. **V11 não foi aplicada nesta sessão**, pois os bancos não estavam acessíveis.

Coleções novas de suporte: `webhook_inbox`, `message_operations`, `channel_operations`, `channel_runtime`, `conversation_write_leases`, `contacts`. Mensagens e conversas continuam nas coleções canônicas existentes.

Índices adicionados: estado/próxima tentativa e canal/data da inbox; TTL de envelopes; unicidade canal/operationId de auditorias; status/última reconciliação das mensagens; unicidade canal/telefone de contatos. Índices existentes de canal/externalMessageId, canal/operationId e conversa/data/ID continuam ativos. Campos opcionais novos não exigem reset ou reescrita de documentos antigos.

## Matriz funcional final

“PRONTO” nesta tabela significa implementação e verificação isolada especificada; não substitui a homologação integrada pendente.

| Funcionalidade                             | Status                                        | Validação                                            | Pendência                                     |
| ------------------------------------------ | --------------------------------------------- | ---------------------------------------------------- | --------------------------------------------- |
| Resolver por canal / Mock / Twilio         | PRONTO                                        | testes de Strategy existentes                        | conta Twilio real fora do escopo              |
| Envio idempotente e UNKNOWN                | PRONTO                                        | regressão isolada e tentativa antes do adapter       | carga/concorrência com bancos reais           |
| Callback durável / deduplicação            | PRONTO                                        | claim duplicado, falha e classificação em testes     | índice/lease/TTL no MongoDB real              |
| Contratos Z-API de texto/reply/edit        | PRONTO                                        | fixtures HTTP                                        | mensagem real elegível                        |
| Forward/reação/remoção/exclusão/leitura    | PRONTO no contrato local                      | método/JSON/query em stubs, preservação do conteúdo  | callbacks/limites da instância real           |
| Status monotônico / callback cedo          | PRONTO                                        | regressão de READ→DELIVERED e lógica de replay       | ordenação real sob concorrência               |
| FromMe / eco / reconciliação               | PARCIAL na homologação                        | implementação e cenário integrado opt-in             | executar cenário tardio contra bancos         |
| Contatos e ciclos                          | PARCIAL na homologação                        | normalização/LID testados, vinculação incremental    | validar registros/índices/ciclos reais        |
| Transferência/revogação                    | PRONTO na checagem isolada                    | STOMP outbound nega acesso revogado, leases testados | duas sessões e notificação PostgreSQL real    |
| QR Code / Challenge / conexão              | PRONTO no contrato local                      | status e QR fixture                                  | escaneamento e pareamento reais               |
| Reinício/desconexão                        | PRONTO no contrato local                      | contratos remotos + POST interno confirmado          | não executados em instância real              |
| Tela de canais / ações na Inbox            | PARCIAL na homologação                        | build/lint e revisão estática                        | navegação visual, foco, scroll, nove larguras |
| Leitura interna separada                   | PRONTO preservado                             | código existente e política explícita                | teste integrado com dois leitores             |
| Mensagens internas/templates               | PRONTO preservado no código                   | serviços mantidos fora do provider                   | cenário integrado de regressão                |
| Segunda instância Z-API                    | NÃO IMPLEMENTADO                              | slot atual rejeita canal divergente                  | acrescentar referência segura específica      |
| Evento remoto de exclusão não documentado  | NÃO IMPLEMENTADO intencionalmente             | sem payload presumido                                | contrato oficial suficiente se necessário     |
| LID sem telefone verificável               | PARCIAL intencionalmente                      | não inventa telefone, falha rastreável               | resolução oficial homologada                  |
| Autenticação de produção                   | NÃO IMPLEMENTADO nesta tarefa                 | preservado guard/sessão dev existentes               | etapa de publicação, sem deploy agora         |
| Z-API conectada / callbacks HTTPS públicos | BLOQUEADO POR CREDENCIAIS/AMBIENTE AUTORIZADO | nenhuma chamada real executada                       | checklist operacional abaixo                  |

## Verificações e erros encontrados durante o trabalho

Os resultados finais de comandos e contagens estão na seção de execução abaixo. Não foram criados brokers, containers adicionais ou uma infraestrutura nova de testes. Fixtures usam os mecanismos JUnit/Mockito/Spring já existentes.

Foram encontrados imports Java, uma captura de variável não efetivamente final e expectativas antigas de fixtures/mocks durante a implementação. Foram corrigidos, incluindo testes antigos que não executavam o novo coordenador no mock e um teste de envio que ainda omitia operationId. Os erros de contrato sanitizados são verificados pelo campo `code`, mantendo a mensagem externa genérica.

O teste integrado `LocalFlowIntegrationTest.mockReleaseActionsAndUnknownReconciliationUseCanonicalPersistence` foi acrescentado ao mecanismo opt-in existente. Ele cobre ações canônicas, política de leitura, idempotência, status fora de ordem, eco tardio, correlação e desconexão/reconexão Mock. **Não foi executado aqui.**

## Checklist de integração real — ainda não executado

- Credenciais de instância/Client-Token autorizadas e canal correspondente.
- Status remoto e número esperado confirmados.
- QR/Challenge concluídos quando necessários.
- HTTPS público verdadeiro, segredo de callback e redação de logs de URL.
- PUTs de cadastro de callbacks aceitos e callback autenticado recebido.
- Recebimento/envio com telefone consentido e sem tráfego de produção.
- Delivery/read/reply/edit/forward/reação/exclusão elegível comprovados.
- FromMe da API e aplicativo sem duplicações.
- Timeout/reconciliação avaliados sem retries cegos.
- Desconexão/reconexão observadas; sem operações destrutivas como teste padrão.
- Duas sessões ADMIN/ATTENDANT, transferência, revogação e notificações verificadas.

Nenhum item dessa lista foi marcado como executado por inferência. Para habilitar, configurar e demonstrar, siga o [guia operacional](README.md).

## Execução final

- `mvn test` com agente Mockito explícito: **65 testes declarados; 49 executados; 16 integrações opt-in ignoradas; zero falhas/erros**.
- Frontend: **9 testes passaram**, incluindo versão de mensagem e progressão do status Mock no mesmo timestamp.
- **10 casos de contrato HTTP Z-API passaram**, sem conexão de rede, incluindo timeout, resposta grande e redação dos logs.
- 53 assinaturas de injeção AngularJS verificadas estaticamente, sem divergências.
- `mvn -DskipTests package`, `npm run build`, `npm run lint:fix`, `npm run lint` (inclui formatação, ESLint, Checkstyle e shell) e `git diff --check`: **passaram**.
- Não houve execução real de Flyway V11, índices novos em bancos do usuário, HTTP autenticado com persistência real, STOMP em sockets reais ou inspeção visual em navegador.
- Docker: socket recusado. TCP em localhost 4001, 4003 e 4004: PermissionError/errno 1. Nenhum banco foi apagado, resetado ou populado nesta tarefa.
- Não houve chamada Z-API/Twilio real, mensagem real, QR real, restart/disconnect real, commit, push ou deploy.

## Arquivos desta tarefa

Inventário por comparação com o início desta tarefa; alterações anteriores do workspace foram preservadas. O teste frontend existente foi identificado separadamente. AGENTS.md e migrations V1–V10 mantiveram o hash original.

### Criados (30)

- `docs/whatsapp/README.md`
- `docs/whatsapp/WHATSAPP_MODULE_STATUS.md`
- `frontend/src/features/channels/channels-page.directive.js`
- `frontend/src/features/channels/channels-page.html`
- `frontend/src/features/channels/channels.css`
- `src/main/java/com/wxn/chatbot/auth/config/ConversationWriteGuard.java`
- `src/main/java/com/wxn/chatbot/contact/ContactRegistry.java`
- `src/main/java/com/wxn/chatbot/contact/ExternalContactRepository.java`
- `src/main/java/com/wxn/chatbot/conversation/repository/ConversationWriteLeaseRepository.java`
- `src/main/java/com/wxn/chatbot/conversation/service/ConversationWriteCoordinator.java`
- `src/main/java/com/wxn/chatbot/message/controller/MessageMutationController.java`
- `src/main/java/com/wxn/chatbot/message/repository/MessageMutationRepository.java`
- `src/main/java/com/wxn/chatbot/message/service/MessageMutationService.java`
- `src/main/java/com/wxn/chatbot/message/service/MessageReconciliationService.java`
- `src/main/java/com/wxn/chatbot/message/service/MessageStatusService.java`
- `src/main/java/com/wxn/chatbot/websocket/ChannelAudienceNotifier.java`
- `src/main/java/com/wxn/chatbot/whatsapp/config/WhatsAppIndexes.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/ChannelConnection.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/ConnectionInstructions.java`
- `src/main/java/com/wxn/chatbot/whatsapp/operations/ChannelOperationsController.java`
- `src/main/java/com/wxn/chatbot/whatsapp/operations/ChannelOperationsRepository.java`
- `src/main/java/com/wxn/chatbot/whatsapp/operations/ChannelOperationsService.java`
- `src/main/java/com/wxn/chatbot/whatsapp/repository/WebhookInboxRepository.java`
- `src/main/java/com/wxn/chatbot/whatsapp/zapi/ZApiWebhookLimitFilter.java`
- `src/main/resources/db/migration/V11__notification_delivery_deduplication.sql`
- `src/test/java/com/wxn/chatbot/conversation/service/ConversationWriteCoordinatorTest.java`
- `src/test/java/com/wxn/chatbot/message/MessageReleaseRegressionTest.java`
- `src/test/java/com/wxn/chatbot/websocket/OutboundAuthorizationTest.java`
- `src/test/java/com/wxn/chatbot/whatsapp/zapi/ZApiContractTest.java`
- `src/test/java/com/wxn/chatbot/whatsapp/zapi/ZApiWebhookTest.java`

### Modificados (65)

- `.env.example`
- `README.md`
- `docs/zapi.md`
- `frontend/src/app/app.routes.js`
- `frontend/src/app/main.js`
- `frontend/src/core/socket/socket.service.js`
- `frontend/src/features/channels/channels.service.js`
- `frontend/src/features/chats/directives/chat-header.directive.js`
- `frontend/src/features/chats/templates/chat-header.html`
- `frontend/src/features/chats/templates/chat-layout.html`
- `frontend/src/features/messages/directives/message-bubble.directive.js`
- `frontend/src/features/messages/message-actions.service.js`
- `frontend/src/features/messages/message-state.js`
- `frontend/src/features/messages/messages.controller.js`
- `frontend/src/features/messages/templates/message-bubble.html`
- `frontend/src/features/messages/templates/message-composer.html`
- `frontend/src/features/notifications/notification-bell.html`
- `frontend/src/features/settings/settings-catalog.js`
- `frontend/tests/message-state.test.mjs`
- `src/main/java/com/wxn/chatbot/auth/config/AccountSocketAccess.java`
- `src/main/java/com/wxn/chatbot/auth/config/AccountWebConfiguration.java`
- `src/main/java/com/wxn/chatbot/conversation/document/Conversation.java`
- `src/main/java/com/wxn/chatbot/conversation/mapper/ConversationMapper.java`
- `src/main/java/com/wxn/chatbot/conversation/repository/ConversationOperations.java`
- `src/main/java/com/wxn/chatbot/conversation/repository/ConversationOperationsImpl.java`
- `src/main/java/com/wxn/chatbot/conversation/repository/ConversationRepository.java`
- `src/main/java/com/wxn/chatbot/conversation/service/ConversationActivityService.java`
- `src/main/java/com/wxn/chatbot/conversation/service/ConversationService.java`
- `src/main/java/com/wxn/chatbot/conversation/service/ConversationTransitionService.java`
- `src/main/java/com/wxn/chatbot/member/service/MemberService.java`
- `src/main/java/com/wxn/chatbot/message/document/Message.java`
- `src/main/java/com/wxn/chatbot/message/mapper/MessageMapper.java`
- `src/main/java/com/wxn/chatbot/message/repository/MessageOperations.java`
- `src/main/java/com/wxn/chatbot/message/repository/MessageOperationsImpl.java`
- `src/main/java/com/wxn/chatbot/message/repository/MessageRepository.java`
- `src/main/java/com/wxn/chatbot/message/service/MessageActionsService.java`
- `src/main/java/com/wxn/chatbot/message/service/MessageService.java`
- `src/main/java/com/wxn/chatbot/messagelog/model/MessageLogEvent.java`
- `src/main/java/com/wxn/chatbot/messagelog/repository/MessageLogRepository.java`
- `src/main/java/com/wxn/chatbot/messagelog/service/MessageLogService.java`
- `src/main/java/com/wxn/chatbot/notification/repository/NotificationRepository.java`
- `src/main/java/com/wxn/chatbot/notification/service/NotificationService.java`
- `src/main/java/com/wxn/chatbot/websocket/DevelopmentStompInterceptor.java`
- `src/main/java/com/wxn/chatbot/websocket/StompConversationEventBridge.java`
- `src/main/java/com/wxn/chatbot/whatsapp/controller/MockWebhookController.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/MessageCapability.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/NormalizedConversation.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/NormalizedInboundMessage.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/NormalizedMessage.java`
- `src/main/java/com/wxn/chatbot/whatsapp/provider/MockWhatsAppProvider.java`
- `src/main/java/com/wxn/chatbot/whatsapp/provider/WhatsAppProvider.java`
- `src/main/java/com/wxn/chatbot/whatsapp/service/MockIngressService.java`
- `src/main/java/com/wxn/chatbot/whatsapp/zapi/ZApiAdministrationController.java`
- `src/main/java/com/wxn/chatbot/whatsapp/zapi/ZApiAdministrationService.java`
- `src/main/java/com/wxn/chatbot/whatsapp/zapi/ZApiClient.java`
- `src/main/java/com/wxn/chatbot/whatsapp/zapi/ZApiConfiguration.java`
- `src/main/java/com/wxn/chatbot/whatsapp/zapi/ZApiDtos.java`
- `src/main/java/com/wxn/chatbot/whatsapp/zapi/ZApiWebhookController.java`
- `src/main/java/com/wxn/chatbot/whatsapp/zapi/ZApiWebhookMapper.java`
- `src/main/java/com/wxn/chatbot/whatsapp/zapi/ZApiWebhookService.java`
- `src/main/java/com/wxn/chatbot/whatsapp/zapi/ZApiWhatsAppProvider.java`
- `src/test/java/com/wxn/chatbot/LocalFlowIntegrationTest.java`
- `src/test/java/com/wxn/chatbot/message/MessageServiceTest.java`
- `src/test/java/com/wxn/chatbot/websocket/DevelopmentStompInterceptorTest.java`
- `src/test/java/com/wxn/chatbot/whatsapp/ProviderTest.java`

### Removidos (0)

Nenhum.
