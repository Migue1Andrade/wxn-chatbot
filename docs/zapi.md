> Referência atual: [guia WhatsApp](whatsapp/README.md) e [estado de homologação](whatsapp/WHATSAPP_MODULE_STATUS.md). As limitações descritas neste documento anterior devem ser conferidas no estado atual.

# WhatsApp de texto: Z-API, Mock e Twilio

A WXN é single-organization. O canal é a fronteira de roteamento: cada conversa mantém `channelId` e
`provider`. Credenciais não são colunas do canal. Não há tenant, infraestrutura adicional, mídia ou BOT
nesta implementação.

## Strategy e configuração

`WhatsAppProviderResolver` recebe os adapters por DI e seleciona a Strategy pelo canal da conversa.
`WhatsAppProvider` define envio, resposta citada, encaminhamento nativo, exclusão e capacidades. Os modelos
`SendMessageRequest`, `ForwardMessageRequest`, `DeleteMessageRequest`, `SendMessageResult`,
`NormalizedInboundMessage` e `NormalizedStatusEvent` são canônicos. O client e os DTOs Z-API ficam no pacote
`whatsapp.zapi`; serviços de mensagens não conhecem endpoints ou DTOs Z-API.

| Operação            | ZAPI                          | MOCK em dev     | TWILIO              |
| ------------------- | ----------------------------- | --------------- | ------------------- |
| SEND_TEXT           | Sim                           | Simulado        | Sim                 |
| REPLY_TEXT          | Sim                           | Simulado        | Não implementado    |
| FORWARD_TEXT        | Sim, mesmo canal/instância    | Simulado        | Não implementado    |
| DELETE_FOR_EVERYONE | Saídas com ID externo         | Simulado        | Não implementado    |
| DELETE_FOR_ME       | Conta conectada compartilhada | Simulado        | Não implementado    |
| MESSAGE_STATUS      | Webhooks                      | Endpoint manual | Webhooks existentes |
| EDIT_TEXT           | Não suportado                 | Não suportado   | Não implementado    |

Copiar e reutilizar texto são operações locais da interface. Não são edição remota. Não foi encontrado
endpoint oficial de edição de texto no catálogo consultado em 28/10/2026; referências a edição de eventos
ou a erros de edição não constituem um contrato de edição de texto.

Configure no `.env`, sem versionar credenciais:

```dotenv
ZAPI_BASE_URL=https://api.z-api.io/instances/SEU_INSTANCE_ID/token/SEU_TOKEN
ZAPI_CLIENT_TOKEN=SEU_CLIENT_TOKEN
ZAPI_CHANNEL_ID=123
ZAPI_WEBHOOK_SECRET=SUBSTITUA_POR_SEGREDO_ALEATORIO_COM_32_OU_MAIS_CARACTERES
ZAPI_PUBLIC_BASE_URL=https://sua-url-publica.example
```

`ZAPI_BASE_URL` é exclusivamente a URL **de saída**, completa, sem operação e sem barra final. O client
acrescenta uma operação uma única vez. Aceita somente HTTPS, host `api.z-api.io`, porta padrão e caminho
`/instances/{id}/token/{token}`, sem query, fragmento ou userinfo. Não há URL fornecida pelo navegador.
`ZAPI_PUBLIC_BASE_URL` é a origem **de entrada** da WXN. Não é a URL da API externa.

Crie o canal como ADMIN por `POST /api/whatsapp/channels`:

```json
{
  "name": "WhatsApp Z-API",
  "provider": "ZAPI",
  "phoneNumber": "+5511999999999",
  "externalChannelId": "SEU_INSTANCE_ID",
  "status": "ACTIVE"
}
```

Use o ID retornado em `ZAPI_CHANNEL_ID` e reinicie o backend com as variáveis exportadas. O telefone e o
ID externo são metadados públicos do canal, não tokens. A migration V5 amplia a constraint de provider sem
alterar migrations anteriores. A aplicação inicia com Mock sem nenhuma variável Z-API preenchida.
`ZApiConfiguration.requireChannel` resolve e valida a configuração pelo canal; para adicionar credenciais
por canal futuramente, substitua essa resolução por uma fonte segura, preservando os contratos canônicos.
Não faça chamadas diretas ao client por fluxos futuros de BOT.

## Endpoints externos confirmados

Todos usam `Client-Token` no backend e JSON quando houver body:

| Método | Sufixo acrescentado à ZAPI_BASE_URL | Utilização                                                               |
| ------ | ----------------------------------- | ------------------------------------------------------------------------ |
| POST   | `/send-text`                        | Texto; `messageId` opcional referencia a mensagem original para resposta |
| POST   | `/forward-message`                  | `phone` destino, `messageId` original, `messagePhone` chat de origem     |
| DELETE | `/messages`                         | Query `messageId`, `phone`, `owner`, `deleteForMe`                       |
| GET    | `/status`                           | Estado de conexão, consultado explicitamente por ADMIN                   |
| PUT    | `/update-webhook-received`          | Registro de recebimento                                                  |
| PUT    | `/update-webhook-received-delivery` | Recebimento incluindo mensagens do próprio número                        |
| PUT    | `/update-webhook-message-status`    | Status                                                                   |
| PUT    | `/update-webhook-delivery`          | Confirmação de envio ou falha assíncrona                                 |
| PUT    | `/update-notify-sent-by-me`         | Habilita `notifySentByMe`                                                |

Uma resposta de envio HTTP 200 significa aceitação da operação, não entrega ao destinatário. `messageId`
é o identificador WhatsApp usado nas operações seguintes. `zaapId` fica em `providerTrackingId`; `id` fica
em `providerResponseId`, sem supor equivalência entre eles. A confirmação de exclusão exige `value: true`.

## API interna e permissões

As rotas internas preservam sessão HTTP, cookie e CSRF, além da proteção de desenvolvimento existente.
Use `GET /api/auth/session`, login e o novo `csrfToken`, conforme [contas](user-accounts.md).
O ator e o canal de envio são resolvidos no servidor; `memberId`, telefones de origem, `owner` e IDs
externos fornecidos por clientes não determinam essas operações.

| Método | Rota WXN                                             | Body ou parâmetros                              |
| ------ | ---------------------------------------------------- | ----------------------------------------------- |
| POST   | `/api/conversations/{id}/messages`                   | `content`, `operationId`                        |
| POST   | `/api/conversations/{id}/messages/{messageId}/reply` | `content`, `operationId`                        |
| POST   | `/api/messages/{messageId}/forward`                  | `destinationConversationId`, `operationId`      |
| DELETE | `/api/messages/{messageId}`                          | Query `intent`, `operationId`                   |
| GET    | `/api/conversations/{id}/message-capabilities`       | Capacidades do provider e permissão operacional |
| GET    | `/api/whatsapp/channels/{id}/zapi/connection`        | ADMIN; somente estado, sem credenciais          |
| POST   | `/api/whatsapp/channels/{id}/zapi/webhooks`          | ADMIN; registro explícito, sem body             |

`operationId` deve ter de 16 a 100 caracteres alfanuméricos, `_` ou `-`; UUID atende ao contrato. Repetir
uma operação com o mesmo identificador retorna a tentativa persistida; reutilizá-lo com outro conteúdo,
ator, origem ou destino retorna conflito. Não gere outro identificador para repetir uma requisição cujo
resultado seja incerto. A interface mantém o ID enquanto o mesmo rascunho aguarda confirmação.

ADMIN pode operar atendimentos abertos; seu usuário é auditado em `actorUserId`, mesmo sem vínculo de atendente. ATTENDANT só opera os atribuídos ao próprio
membro autenticado. Respostas exigem mensagem na mesma conversa/canal. Encaminhamento exige origem e
destino acessíveis no mesmo canal e provider; não existe fallback silencioso para texto entre instâncias.
O menu usa capacidades da mensagem, permissões, estado da conversa e existência de ID externo.
A escolha de destinos lista atendimentos abertos no mesmo canal, com paginação.

As intenções de exclusão são `DELETE_FOR_EVERYONE` e `DELETE_FOR_ME`. A primeira exige autoria do número
conectado; as restrições remotas adicionais são decididas pelo provider. A segunda afeta a conta WhatsApp
**compartilhada por todos os atendentes**, não uma visualização privada. A interface pede confirmação
explícita. Não há exclusão física nem funcionalidade de ocultação por usuário.

## Webhooks e segurança

A Z-API envia **POST JSON** para a WXN, enquanto a WXN registra as URLs por **PUT** na Z-API.
Não há registro automático na inicialização. Depois de configurar uma URL HTTPS pública, execute como
ADMIN `POST /api/whatsapp/channels/{id}/zapi/webhooks`. A operação registra recebimento, recebimento de
mensagens próprias, status e confirmação de envio, e habilita `notifySentByMe: true`. A URL de recebimento
com mensagens próprias tem prioridade sobre a URL simples, conforme documentação do provider.
Configuração é sequencial; se houver falha parcial, corrija a configuração e repita explicitamente.

| Callback WXN                                           | Payload                 |
| ------------------------------------------------------ | ----------------------- |
| `/api/webhooks/zapi/received?secret=SEGREDO_WXN`       | `ReceivedCallback`      |
| `/api/webhooks/zapi/message-status?secret=SEGREDO_WXN` | `MessageStatusCallback` |
| `/api/webhooks/zapi/sent?secret=SEGREDO_WXN`           | `DeliveryCallback`      |

Não foi encontrada assinatura criptográfica ou configuração de header customizado para callbacks nas
referências consultadas. Por isso, a WXN registra um segredo aleatório **independente** na query da URL
configurada, verifica-o em tempo constante e valida também instância, canal ativo e número conectado no
recebimento. Essa autenticação é por posse de segredo; não é assinatura do corpo nem atestado da origem
IP. O `Client-Token` documentado protege chamadas **à Z-API**, não comprova autenticidade de callbacks.
Mantenha HTTPS, não habilite logs de query de callback e não compartilhe URLs registradas. Não é utilizado
o token da API no endereço público. O client não registra URLs, corpos de erro, credenciais ou causas de
exceções HTTP; logging de transporte Spring/JDK fica desabilitado na configuração da aplicação.

Apenas o namespace de webhooks ignora sessão/CSRF e continua sujeito à autenticação própria. Endpoints
internos não foram tornados públicos. Sem segredo configurado não há callback aceito.

A Z-API exige HTTPS público para entrega real. Não há deploy nem túnel automático nesta entrega.
Os arquivos em [examples/zapi](examples/zapi) permitem simular callbacks por HTTP local após configurar
um canal ZAPI e as variáveis correspondentes. Troque `instanceId`, `connectedPhone` e `messageId` pelos
valores do seu ambiente; não é necessário disparar um envio externo para simular recebimento:

```bash
curl --request POST \
  "http://localhost:4001/api/webhooks/zapi/received?secret=${ZAPI_WEBHOOK_SECRET}" \
  --header 'Content-Type: application/json' \
  --data-binary @docs/examples/zapi/received.json
```

Repita o mesmo payload para conferir idempotência. `fromMe: true` e `fromApi: false` representam saída
pelo aplicativo WhatsApp: `origin=WHATSAPP_APP`, sem membro/atendente fictício. O eco de uma mensagem WXN
já associada ao ID externo atualiza a tentativa existente. Ecos com `fromApi: true` ainda não associados
ficam auditados em `message_logs`; não são transformados em cópias de mensagens independentes.
Envios e ecos são serializados por canal no processo local, evitando a corrida callback/resposta HTTP.
Canais diferentes permanecem independentes. Se `fromApi` não vier no payload e existir tentativa PENDING
ou UNKNOWN sem correspondência, o eco fica apenas auditado para evitar duplicação ou atribuição falsa.
Sem tentativa pendente, uma saída sem indicação de origem recebe `WHATSAPP_EXTERNAL`, sem atendente;
não é rotulada como envio comprovadamente feito pelo aplicativo.

## Estados, confiabilidade e persistência

O fluxo normal é `PENDING → ACCEPTED → SENT → DELIVERED → READ`. `RECEIVED` é o estado de entrada;
no callback de status Z-API, `RECEIVED` mapeia para **DELIVERED** de uma saída. `READ_BY_ME` é leitura da
própria conta e não significa leitura pelo destinatário; `PLAYED` é de mídia. Ambos ficam auditados sem
alterar o estado de entrega de texto. Callbacks desconhecidos são preservados como auditoria sem
corromper mensagens. Eventos de status repetidos não duplicam logs nem regredem READ para SENT.

`FAILED` representa falha conhecida. `UNKNOWN` representa confirmação remota incerta, como timeout,
falha de rede ou HTTP 5xx: não significa que o destinatário não recebeu. Não há retry automático de envio.
O client usa conexão de 5 segundos, leitura de 20 segundos e não segue redirects. A interface limita a
espera HTTP a 30 segundos. Falhas de configuração e HTTP 4xx ficam registradas na tentativa.

Quando existe ID externo ou `zaapId` associado, callbacks reconciliam status com a tentativa. Callbacks
que chegam antes da associação do ID são persistidos e reaplicados após a resposta de envio. Se a resposta
se perde antes de qualquer identificador remoto ser obtido, a Z-API não fornece neste contrato uma chave
local de correlação: não é seguro associar só por texto/horário. O registro UNKNOWN e o eco auditado
permanecem para conferência operacional, sem retry cego ou alegação de entrega. Reinício durante chamada
externa também exige conferência de tentativas PENDING. Não há reconciliação por consulta remota de
histórico ou recuperação de mensagens que nunca chegaram por webhook.

A coleção `messages` mantém os campos anteriores e acrescenta, de forma nullable para documentos antigos:
`operationId`, `replyToMessageId`, `replyToExternalMessageId`, `replyPreview`, `forwardedFromMessageId`,
`origin`, `actorUserId`, `providerTrackingId`, `providerResponseId`, `deletedAt`, `deletionIntent`, `deletedByUserId`,
`deleteOperationId` e `deleteState`. Conteúdo original é preservado para auditoria, mas a interface mostra
tombstone após confirmação remota. O estado da exclusão é independente do estado de entrega:
`PENDING`, `CONFIRMED`, `FAILED`, `UNKNOWN`. Uma exclusão incerta não é repetida automaticamente.

Índices novos: `(channelId, operationId)` único parcial para strings e `(channelId, providerTrackingId)`.
Preservam-se `(channelId, externalMessageId)` único parcial, paginação por conversa/data/ID e demais índices.
O canal identifica de forma inequívoca o provider/instância; IDs externos iguais em canais diferentes
não colidem. Não altere o provider ou a instância de um canal com histórico; crie outro canal.
`message_logs.eventKey` mantém idempotência de callbacks e auditoria de tentativa, resultado e exclusão.
Não há collection paralela de mensagens.

Exemplos no `mongosh`, conectado ao banco configurado em `MONGO_DB`:

```javascript
db.messages.find({ conversationId: ObjectId('ID_DA_CONVERSA') }).sort({ createdAt: 1, _id: 1 });
db.messages.find({ channelId: 123, status: { $in: ['UNKNOWN', 'PENDING'] } });
db.messages.find({ channelId: 123, deleteState: 'UNKNOWN' });
db.message_logs.find({ channelId: 123 }).sort({ createdAt: -1 }).limit(30);
db.messages.getIndexes();
```

Eventos STOMP continuam em `/topic/conversations`, com envelope canônico existente:
`message.received`, `message.sent`, `message.status.updated` e eventos de conversa. Criação, resultado e
exclusão são persistidos antes da publicação. A UI mescla REST e WebSocket por ID, preserva paginação e
reconcilia o histórico ao reconectar. A citação localiza a mensagem original se carregada; caso contrário,
informa que é necessário carregar mensagens anteriores. Texto é renderizado com binding, nunca HTML bruto.

## Verificação manual com Mock

Com perfil `dev`, autenticação ADMIN e sessão/CSRF válidos:

1. Crie ou utilize um canal MOCK ativo com telefone configurado.
2. Envie `POST /api/dev/whatsapp/mock/inbound` com `channelId`, `externalMessageId`, `senderPhone` e
   `content`; isso cria uma conversa se necessário. Repetir o ID não duplica a mensagem.
3. Use a UI para assumir a conversa, enviar, responder, copiar, encaminhar e excluir.
4. Para destino, crie outra entrada no mesmo canal, assuma a conversa e abra o diálogo de encaminhamento.
5. Simule entrega/leitura por `POST /api/dev/whatsapp/mock/status`:

```json
{
  "channelId": 1,
  "externalMessageId": "mock-ID_CANAL-ID_MENSAGEM",
  "status": "READ"
}
```

Envie SENT depois de READ para conferir que o estado não regride. Copiar não chama backend nem altera o
histórico. Mock produz IDs próprios e usa as mesmas relações, tombstones, logs e eventos das operações
reais, sem afirmar que houve envio externo. Sua simulação é exclusiva de desenvolvimento.

## Referências oficiais

- [Envio](https://developer.z-api.io/message/send-text),
  [resposta](https://developer.z-api.io/message/reply-message),
  [encaminhamento](https://developer.z-api.io/message/forward-message),
  [exclusão](https://developer.z-api.io/message/delete-message).
- [Introdução a webhooks](https://developer.z-api.io/webhooks/introduction),
  [recebimento](https://developer.z-api.io/webhooks/on-message-received),
  [exemplos](https://developer.z-api.io/webhooks/on-message-received-examples),
  [mensagens próprias](https://developer.z-api.io/webhooks/on-message-received-and-delivery),
  [notificar próprias](https://developer.z-api.io/webhooks/update-notify-sent-by-me).
- [Confirmação de envio](https://developer.z-api.io/webhooks/on-message-send),
  [exemplos de confirmação](https://developer.z-api.io/webhooks/on-message-send-examples),
  [status](https://developer.z-api.io/webhooks/on-whatsapp-message-status-changes),
  [conexão](https://developer.z-api.io/instance/status).
- [ID/token](https://developer.z-api.io/security/introduction),
  [Client-Token](https://developer.z-api.io/security/client-token).

## Limitações operacionais

A integração de saída real depende de credenciais válidas, canal vinculado, instância conectada e webhooks
registrados com sucesso. Encaminhamento depende do webhook e da disponibilidade remota da mensagem
original. A simulação Mock não comprova que esses pré-requisitos externos foram atendidos. O tratamento
local não tenta adivinhar prazo máximo de exclusão: a Z-API pode rejeitar uma ação remota e o tombstone só
é aplicado após confirmação positiva.
