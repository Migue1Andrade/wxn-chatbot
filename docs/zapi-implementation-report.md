# Relatório de implementação — WhatsApp texto e Z-API

Este relatório descreve esta implementação sobre uma árvore que já continha alterações locais. As
alterações anteriores de usuários, setores, autenticação e organização única foram preservadas. Não houve
commit, push, deploy, mudança em AGENTS.md ou criação de suíte automatizada.

**A. Arquivos criados**

- `src/main/java/com/wxn/chatbot/whatsapp/zapi/`: `ZApiConfiguration.java`, `ZApiClient.java`,
  `ZApiDtos.java`, `ZApiWhatsAppProvider.java`, `ZApiWebhookMapper.java`, `ZApiWebhookService.java`,
  `ZApiWebhookController.java`, `ZApiAdministrationService.java`, `ZApiAdministrationController.java`.
- `whatsapp/model/MessageCapability.java`, `ForwardMessageRequest.java`, `DeleteMessageRequest.java`.
- `message/service/MessageActionsService.java`, `message/controller/MessageActionsController.java`.
- `src/main/resources/db/migration/V5__allow_zapi_channel_provider.sql`.
- `frontend/src/features/messages/message-actions.service.js`.
- `docs/zapi.md`, este relatório e quatro exemplos JSON em `docs/examples/zapi/`:
  `received.json`, `from-me.json`, `sent.json`, `message-status.json`.

Os caminhos Java abreviados acima estão sob `src/main/java/com/wxn/chatbot/`.

**B. Arquivos modificados por esta tarefa**

- Configuração/documentação: `.env.example`, `README.md`, `src/main/resources/application.yml`.
- Backend: `AccountAccessInterceptor`, `ApiErrorHandler`, `MongoConfiguration`, `MessageController`,
  `MessageDtos`, `Message`, `MessageMapper`, `MessageRepository`, `MessageOperations`,
  `MessageOperationsImpl`, `MessageService`, `MessageStatusTransitions`, `MessageLog`, `MessageLogEvent`,
  `MessageLogService`, `ProviderType`, `SendMessageResult`, `NormalizedMessage`, `NormalizedInboundMessage`,
  `WhatsAppProvider`, `WhatsAppProviderResolver`, `MockWhatsAppProvider`, `TwilioWhatsAppProvider`,
  `MockIngressService`, `MockWebhookController`.
- Frontend: `app/main.js`, `core/http/api-client.service.js`,
  `features/chats/templates/chat-layout.html`, `features/messages/messages.service.js`,
  `messages.controller.js`, `message-sending.service.js`, diretivas `message-bubble` e `message-composer`,
  seus templates e `shared/styles/main.css`.
- Compatibilidade dos exemplos existentes: `scripts/seed-chat-examples.mjs` e coleção Postman existente,
  acrescentando a chave local de operação aos envios.

**C. Contratos Strategy**

`WhatsAppProvider` mantém `type`, `capabilities`, `send`, `normalizeInbound`; acrescenta capacidades
explícitas por operação, validação local do canal, resposta, encaminhamento e exclusão. Operações não
implementadas falham explicitamente. `WhatsAppProviderResolver` escolhe pelo canal. Os pedidos/resultados
e eventos normalizados são canônicos. O adapter Z-API concentra normalização JSON e DTOs HTTP próprios.

**D. Providers preservados**

Mock continua disponível em desenvolvimento. Implementa simulação dos novos contratos e status manual.
Twilio preserva envio e webhooks assinados, sem anunciar resposta/encaminhamento/exclusão não implementados.
Não há provider global único, fluxo BOT ou novo domínio de empresas.

**E. Endpoints Z-API implementados**

`POST /send-text`, `POST /forward-message`, `DELETE /messages`, `GET /status`, e os PUTs
`/update-webhook-received`, `/update-webhook-received-delivery`, `/update-webhook-message-status`,
`/update-webhook-delivery`, `/update-notify-sent-by-me`. Todos ficam no client dedicado. A resposta usa
`/send-text` com o ID externo da citação. Nenhum endpoint de edição de texto foi inventado.

**F. Endpoints internos**

- `POST /api/conversations/{id}/messages`.
- `POST /api/conversations/{id}/messages/{messageId}/reply`.
- `POST /api/messages/{messageId}/forward`.
- `DELETE /api/messages/{messageId}?intent=...&operationId=...`.
- `GET /api/conversations/{id}/message-capabilities`.
- ADMIN: `GET /api/whatsapp/channels/{id}/zapi/connection` e
  `POST /api/whatsapp/channels/{id}/zapi/webhooks`.
- Desenvolvimento: `POST /api/dev/whatsapp/mock/status`, preservando `/inbound`.

Ator vem da sessão. ADMIN é auditado pelo usuário, inclusive quando não tem vínculo de atendente.
ATTENDANT exige a conversa atribuída ao próprio membro. Canal, origem, destino, autoria remota e IDs
externos são resolvidos/validados no backend. O frontend recebe capacidades por canal e mensagem.

**G. Estrutura de mensagens**

Mantidas `conversations`, `messages`, `message_logs` e os campos existentes. Metadados novos são nullable
para compatibilidade: operação local, usuário ator, citação interna/externa e prévia, origem de
encaminhamento, origem de envio, IDs auxiliares do provider e auditoria/estado de exclusão. O original
não é fisicamente apagado. A UI mostra tombstone somente após confirmação da operação remota.

**H. Índices MongoDB**

Novo índice único parcial `(channelId, operationId)` e índice `(channelId, providerTrackingId)`.
Preservados os índices de paginação, logs idempotentes e `(channelId, externalMessageId)` único parcial.
O canal identifica provider/instância; IDs iguais em canais distintos não colidem.

**I. Webhooks**

POSTs `/api/webhooks/zapi/received`, `/message-status`, `/sent`, com segredo WXN independente na query,
comparação em tempo constante e validação de instância/canal. Recebimento também confere o telefone
conectado. A documentação consultada não estabelece assinatura criptográfica de callbacks; o mecanismo
implementado autentica a posse do segredo, sem afirmar que assina o corpo ou comprova IP de origem.
Não usa o token da API na URL pública. Registro é explícito, nunca no startup. HTTPS público é exigido
pela Z-API. Exemplos permitem simulação HTTP local sem envio externo.

**J. Eventos STOMP**

Preservados `/topic/conversations`, envelope canônico e `message.received`, `message.sent`,
`message.status.updated`. Tombstones usam atualização canônica de mensagem. Persistência antecede
publicação; callbacks duplicados e status atrasados não geram regressão de READ para SENT.

**K. Frontend**

Menu contextual com copiar, responder, encaminhar, exclusões permitidas e reutilizar texto. Cópia usa
Clipboard API com fallback e feedback, sem endpoint/log/status. Há prévia da citação e localização do
original carregado, modal de destino paginado, confirmação da exclusão compartilhada, loading e bloqueio
de clique duplicado. O menu considera capacidades e permissão; fecha fora/Escape e limita sua posição à
tela. Não há upload nem renderização de texto recebido como HTML. Reutiliza a mesclagem REST/STOMP por
ID, a paginação e a reconciliação de histórico existentes.

**L. Validações**

- Builds Maven com Java 21 e Vite concluídos; lint:fix, lint e diff --check executados.
- Sete testes existentes do frontend passaram.
- Onze testes Java existentes selecionados passaram (mensagens, provider, Twilio e eventos), usando
  Mockito como javaagent já disponível. A tentativa inicial de autoattach foi bloqueada pelo ambiente;
  a execução com javaagent passou.
- Backend iniciado sem Z-API configurada e migration V5 aplicada sem reset dos bancos.
- Exercício manual HTTP com Mock: sessão, envio, IDs externos, repetição idempotente, conflito de operação,
  resposta citada, rejeição de citação de outra conversa, encaminhamento, exclusões com tombstone,
  repetição de exclusão, rejeição de apagar entrada para todos, status monotônico e isolamento de canais.
- Exercício manual HTTP com canal Z-API fictício: validação de segredo e instância, entrada idempotente,
  saída pelo aplicativo sem ator fictício, status READ sem regressão, READ_BY_ME/PLAYED/desconhecidos sem
  corromper entrega e eco fromApi sem criar cópia. Nenhuma chamada externa foi realizada nesse exercício.
- A verificação completa da interface no Chrome headless não foi concluída. Depois da interrupção, o
  sandbox passou a impedir a inicialização do navegador e a conexão do backend aos bancos. Não há
  alegação de aprovação visual, Clipboard real ou reconexão STOMP ponta a ponta.
- Exemplos locais de validação ficaram preservados nos bancos; incluem canais com nomes de validação
  Mock e um canal explicitamente chamado `Simulação local webhook ZAPI`, com identificadores fictícios.
  Eles não configuram uma instância real.

**M. Limitações reais**

Sem edição remota, mídia, BOT, fallback de encaminhamento entre instâncias ou exclusão privada por
atendente. O provider pode rejeitar exclusão por limitações remotas. A compatibilidade com Twilio foi
verificada nos contratos/testes existentes, sem envio real Twilio.

UNKNOWN mantém tentativas de resultado incerto, sem retry cego. Sem qualquer ID remoto retornado, não
existe correlação segura só por conteúdo/horário; ecos são auditados para conferência operacional.
Reinício durante envio também pode exigir conferência de PENDING. As travas por canal são locais ao
processo; execução distribuída não foi introduzida. Não há recuperação de histórico remoto perdido.
Sem `fromApi`, uma saída sem tentativa pendente recebe origem externa indeterminada; havendo tentativa
incerta, permanece auditada até haver correlação segura.

A simulação local não comprova comportamento de uma conta real Z-API. A última revisão de citações de
entrada e de proteção de ecos sem origem explícita está coberta por compilação/revisão; não foi repetido
o exercício HTTP após o bloqueio de acesso local pelo sandbox.

**N. Pendências para conexão real**

Preencher as variáveis descritas em [zapi.md](zapi.md), criar/vincular o canal correto, conectar a instância,
providenciar entrada HTTPS pública e registrar os webhooks explicitamente como ADMIN. Confirmar em conta
real os IDs, a chegada de callbacks de mensagens próprias, encaminhamento e exclusão. Concluir a validação
visual/Clipboard/STOMP em ambiente com permissão de navegador e rede local. Não houve deploy.

Para voltar a executar localmente após a interrupção, siga os comandos do README; a interface Vite
continuou disponível na porta 4002, mas o backend precisa de acesso aos bancos para subir na porta 4001.
