# Entrega — primeira integração WhatsApp

## Implementação

- Preservados Java 21/Spring Boot 3.5.0, V1/V2, entidades existentes, coleções Mongo e frontend.
- V3: telefone nullable em users, validação estrutural E.164 e unicidade por empresa.
- V4: members HUMAN/BOT, ACTIVE/INACTIVE, timestamps/soft delete, FK composta por empresa/usuário e prevenção de humanos ativos duplicados.
- V5: whatsapp_channels com múltiplos canais por empresa e provider por canal, sem secrets na tabela.
- Controllers, services, repositories, DTOs e mappers para empresas, usuários, membros e canais.
- Contratos canônicos de conversa, mensagem, status, envio/resultado e capacidades; BSON centralizado nos mappers.
- WhatsAppProvider, resolver injetado pelo Spring, Mock e adapter oficial Twilio. Seleção conforme canal, sem SDK nos services de conversa/mensagem.
- Recebimento idempotente por empresa/canal/ID externo e conversa individual estável por empresa/canal/telefone.
- Persistência Mongo de mensagens recebidas/enviadas e logs de tentativa, recebimento, envio, falha e callback.
- Atualizações atômicas de status em fragmentos de repository; callbacks antecipados registrados/reconciliados e regressões de status rejeitadas.
- Mock somente em dev sem prod, sem chamadas externas, envio SIMULATED explícito.
- Twilio: texto individual, webhook e callbacks form-urlencoded, assinatura validada pelo SDK, conferência da conta e vínculo seguro fixo de canal/empresa.
- Proteção interna temporária: perfil dev sem prod e X-Dev-Api-Key de pelo menos 32 caracteres; sem autenticação completa.
- Eventos Spring locais via ConversationEventPublisher, preparados para futuro STOMP.
- Coleção/ambiente Postman e README com endpoints, exemplos, execução, Sandbox, ngrok e limitações.

## Testes efetivamente executados

Em 14/10/2026, stack Docker isolada `wxn-whatsapp-it` criada com PostgreSQL 17.11 (porta 55432) e MongoDB 8.3.13 (porta 57017). Nenhum banco local preexistente foi utilizado.

- `mvn verify` com Java 21.0.6 e `WXN_INTEGRATION_TEST=true`: **14 testes, zero falhas, zero erros, zero ignorados**, BUILD SUCCESS.
- Flyway aplicado pelo startup Spring Boot: **V1, V2, V3, V4 e V5**, todos com `success=true`, confirmado por consulta a `flyway_schema_history`.
- Nova inicialização validou as cinco migrations e encontrou schema em V5 sem reaplicar alterações. Hibernate validou o schema.
- Verificados índices únicos parciais das conversas/mensagens e índice de histórico no Mongo real.
- Teste HTTP com bancos reais: empresa, usuário, membros humano/bot, canal Mock, inbound, repetição e concorrência idempotentes, histórico e respostas dos dois membros.
- Testados telefone nullable, E.164, duplicidade por empresa, mesmo telefone em outra empresa, membro humano duplicado, FK de usuário de outra empresa, canal/membro estrangeiros e acesso a conversa/histórico de outra empresa.
- Consultados documentos Mongo legados sem os campos novos; leitura de datas e normalização de tipos legados funcionaram.
- Testadas seleção simultânea de adapters, indisponibilidade de Mock fora do perfil, rejeição de grupos/mídia, IDs Mock previsíveis e distinção entre aceitação e entrega.
- Testadas assinaturas/contas/chaves inválidas e webhook Twilio válido com fixtures locais; callbacks HTTP assinados localmente preservaram READ diante de eventos antigos/repetidos.
- Testada falha de envio Twilio com configuração ausente: tentativa FAILED, mensagem e log mantidos, sem chamada externa.
- Newman: pasta Mock da coleção importável, **12 requisições e 19 assertions, zero falhas**. A execução usou o backend temporário na porta 18080. Após refatoração interna dos mappers/repositories, o fluxo equivalente foi revalidado pelo teste HTTP de integração; o Newman não foi repetido sobre o último JAR.
- `git diff --check`: sem erros. V1/V2, compose.yaml e frontend sem alterações.

Os testes de assinatura Twilio são fixtures locais; não são evidência de comunicação com a conta real Twilio. A primeira tentativa Newman foi bloqueada pelo sandbox e a repetição com acesso autorizado ao localhost passou.

## Não concluído / não validado

- **Envio e recebimento reais no Twilio Sandbox:** não executados, pois não foram fornecidas/configuradas credenciais reais e telefones habilitados.
- Pasta Twilio da coleção Postman não executada contra uma conta real. Entrega no telefone, assinatura originada pela Twilio, callbacks reais, janela de 24 horas e restrições da conta continuam pendentes.
- Túnel ngrok e configuração da Console Sandbox documentados, mas não ativados para uma conta real.
- Não foi reproduzido o upgrade V2→V5 com um backup de produção/populado antes das migrations. A aplicação foi testada em banco isolado, e reexecução/validação com os registros criados nos testes passou; V3 é aditiva e permite NULL.
- Múltiplas credenciais Twilio reais por instância não foram implementadas: somente uma conta/canal é mapeada explicitamente via ambiente nesta versão. Vários canais e diferentes providers simultâneos estão implementados.

Fora do escopo, por solicitação: autenticação completa/autorizações por tenant, frontend, WebSocket funcional, IA/respostas automáticas, grupos reais, templates e envio/download de mídia. Persistência entre coleções e bancos não é transacional; recuperação automática de tentativas interrompidas e idempotency key de envio não foram implementadas.

## Ambiente criado para validação

O backend temporário foi encerrado. A stack isolada e seus volumes permanecem disponíveis para inspeção. Configuração local gerada em `/tmp/wxn-whatsapp-integration.env`, com permissões 0600; não está no repositório. Os relatórios Newman contêm headers de desenvolvimento e ficam somente em /tmp com permissões restritas.

Para reutilizar os bancos isolados:

```bash
set -a
source /tmp/wxn-whatsapp-integration.env
set +a
mvn spring-boot:run
# ou: WXN_INTEGRATION_TEST=true mvn verify
```

Para encerrar a stack preservando os volumes:

```bash
docker compose --env-file /tmp/wxn-whatsapp-integration.env -p wxn-whatsapp-it down
```

Nenhum commit ou push foi realizado. Credenciais reais não foram adicionadas ao repositório.

## Inventário de arquivos

### Modificados (13)

- `.env.example`
- `README.md`
- `pom.xml`
- `src/main/java/com/wxn/chatbot/company/entity/Company.java`
- `src/main/java/com/wxn/chatbot/conversation/document/Conversation.java`
- `src/main/java/com/wxn/chatbot/conversation/repository/ConversationRepository.java`
- `src/main/java/com/wxn/chatbot/message/document/Message.java`
- `src/main/java/com/wxn/chatbot/message/repository/MessageRepository.java`
- `src/main/java/com/wxn/chatbot/messagelog/document/MessageLog.java`
- `src/main/java/com/wxn/chatbot/messagelog/repository/MessageLogRepository.java`
- `src/main/java/com/wxn/chatbot/user/entity/User.java`
- `src/main/java/com/wxn/chatbot/user/repository/UserRepository.java`
- `src/main/resources/application.yml`

### Criados (76)

- `docs/whatsapp-delivery.md`
- `postman/WXN-WhatsApp.postman_collection.json`
- `postman/WXN-local.postman_environment.json`
- `src/main/java/com/wxn/chatbot/common/ActiveStatus.java`
- `src/main/java/com/wxn/chatbot/common/ApiErrorHandler.java`
- `src/main/java/com/wxn/chatbot/common/ApiException.java`
- `src/main/java/com/wxn/chatbot/common/PhoneNumbers.java`
- `src/main/java/com/wxn/chatbot/company/controller/CompanyController.java`
- `src/main/java/com/wxn/chatbot/company/dto/CompanyDtos.java`
- `src/main/java/com/wxn/chatbot/company/mapper/CompanyMapper.java`
- `src/main/java/com/wxn/chatbot/company/service/CompanyService.java`
- `src/main/java/com/wxn/chatbot/config/DevelopmentApiGuard.java`
- `src/main/java/com/wxn/chatbot/config/MongoConfiguration.java`
- `src/main/java/com/wxn/chatbot/conversation/controller/ConversationController.java`
- `src/main/java/com/wxn/chatbot/conversation/event/ConversationEventPublisher.java`
- `src/main/java/com/wxn/chatbot/conversation/event/LocalConversationEventPublisher.java`
- `src/main/java/com/wxn/chatbot/conversation/mapper/ConversationMapper.java`
- `src/main/java/com/wxn/chatbot/conversation/repository/ConversationOperations.java`
- `src/main/java/com/wxn/chatbot/conversation/repository/ConversationOperationsImpl.java`
- `src/main/java/com/wxn/chatbot/conversation/service/ConversationService.java`
- `src/main/java/com/wxn/chatbot/member/controller/MemberController.java`
- `src/main/java/com/wxn/chatbot/member/dto/MemberDtos.java`
- `src/main/java/com/wxn/chatbot/member/entity/Member.java`
- `src/main/java/com/wxn/chatbot/member/entity/MemberType.java`
- `src/main/java/com/wxn/chatbot/member/mapper/MemberMapper.java`
- `src/main/java/com/wxn/chatbot/member/repository/MemberRepository.java`
- `src/main/java/com/wxn/chatbot/member/service/MemberService.java`
- `src/main/java/com/wxn/chatbot/message/controller/MessageController.java`
- `src/main/java/com/wxn/chatbot/message/dto/MessageDtos.java`
- `src/main/java/com/wxn/chatbot/message/mapper/MessageMapper.java`
- `src/main/java/com/wxn/chatbot/message/repository/MessageOperations.java`
- `src/main/java/com/wxn/chatbot/message/repository/MessageOperationsImpl.java`
- `src/main/java/com/wxn/chatbot/message/service/MessageService.java`
- `src/main/java/com/wxn/chatbot/messagelog/service/MessageLogService.java`
- `src/main/java/com/wxn/chatbot/user/controller/UserController.java`
- `src/main/java/com/wxn/chatbot/user/dto/UserDtos.java`
- `src/main/java/com/wxn/chatbot/user/mapper/UserMapper.java`
- `src/main/java/com/wxn/chatbot/user/service/UserService.java`
- `src/main/java/com/wxn/chatbot/whatsapp/config/TwilioProperties.java`
- `src/main/java/com/wxn/chatbot/whatsapp/controller/ChannelController.java`
- `src/main/java/com/wxn/chatbot/whatsapp/controller/MockWebhookController.java`
- `src/main/java/com/wxn/chatbot/whatsapp/controller/TwilioWebhookController.java`
- `src/main/java/com/wxn/chatbot/whatsapp/dto/ChannelDtos.java`
- `src/main/java/com/wxn/chatbot/whatsapp/dto/MockInboundRequest.java`
- `src/main/java/com/wxn/chatbot/whatsapp/entity/WhatsAppChannel.java`
- `src/main/java/com/wxn/chatbot/whatsapp/mapper/ChannelMapper.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/ConversationType.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/Direction.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/MessageType.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/NormalizedConversation.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/NormalizedInboundMessage.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/NormalizedMessage.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/NormalizedMessageStatus.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/NormalizedStatusEvent.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/ProviderCapabilities.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/ProviderType.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/SendMessageRequest.java`
- `src/main/java/com/wxn/chatbot/whatsapp/model/SendMessageResult.java`
- `src/main/java/com/wxn/chatbot/whatsapp/provider/MockWhatsAppProvider.java`
- `src/main/java/com/wxn/chatbot/whatsapp/provider/ProviderSendException.java`
- `src/main/java/com/wxn/chatbot/whatsapp/provider/TwilioWhatsAppProvider.java`
- `src/main/java/com/wxn/chatbot/whatsapp/provider/WhatsAppProvider.java`
- `src/main/java/com/wxn/chatbot/whatsapp/provider/WhatsAppProviderResolver.java`
- `src/main/java/com/wxn/chatbot/whatsapp/repository/WhatsAppChannelRepository.java`
- `src/main/java/com/wxn/chatbot/whatsapp/service/ChannelService.java`
- `src/main/java/com/wxn/chatbot/whatsapp/service/MockIngressService.java`
- `src/main/java/com/wxn/chatbot/whatsapp/service/TwilioIngressService.java`
- `src/main/resources/db/migration/V3__add_user_phone.sql`
- `src/main/resources/db/migration/V4__create_members.sql`
- `src/main/resources/db/migration/V5__create_whatsapp_channels.sql`
- `src/test/java/com/wxn/chatbot/LocalFlowIntegrationTest.java`
- `src/test/java/com/wxn/chatbot/config/DevelopmentApiGuardTest.java`
- `src/test/java/com/wxn/chatbot/member/MemberServiceTest.java`
- `src/test/java/com/wxn/chatbot/message/MessageServiceTest.java`
- `src/test/java/com/wxn/chatbot/whatsapp/ProviderTest.java`
- `src/test/java/com/wxn/chatbot/whatsapp/TwilioIngressTest.java`
