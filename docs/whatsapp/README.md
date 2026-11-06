# Módulo WhatsApp WXN

Referência do contrato do módulo TEXT-only. Consulte o [estado de homologação](WHATSAPP_MODULE_STATUS.md) antes de classificar um ambiente como pronto.

## Arquitetura e pontos de extensão

A WXN continua single-organization e usa `WhatsAppChannel` para resolver o adapter via `WhatsAppProviderResolver`. Mensagens, conversas, contatos externos, leitura interna e eventos usam modelos canônicos. Não há tenant ou provider global de envio.

- `MessageService`: tentativa persistida, idempotência, recebimento e eco externo.
- `MessageStatusService` e `MessageReconciliationService`: transições monotônicas e recuperação sem reenvio.
- `MessageActionsService`: envio, reply, forward nativo e exclusão com autorização.
- `MessageMutationService`: intenções auditadas de edição, reação e leitura remota.
- `ConversationService`, `ConversationManagementService`, `ConversationTransitionService`: identidade, ciclos, estados e atribuição.
- `ConversationWriteCoordinator`: lease MongoDB reentrante por thread, compartilhado com as mutações REST. Janela de 120 segundos; chamadas HTTP externas têm timeout de 20 segundos. Conflitos retornam 409. Não há espera indefinida pelo lease.
- `ContactRegistry`: ID MongoDB nativo por canal + telefone E.164. Não é um segundo cadastro de usuário nem um CRM.
- `WhatsAppProvider`: Strategy canônica; apenas os adapters conhecem os contratos externos.
- `ConversationEventPublisher`: eventos persistidos antes de publicação; consumidores reconciliam via REST após reconexão.

Novos módulos devem consumir esses services/modelos/eventos. Não devem importar `ZApiClient`, interpretar JSON Z-API, construir URLs externas ou conhecer tokens.

A aplicação mantém o controle de sessão, CSRF e chave dev existentes. A dependência Spring Security atual fornece BCrypt; **não há uma nova cadeia de autenticação de produção nesta entrega**. REST e STOMP de operação permanecem no ambiente dev conforme o guard existente. Isto precisa ser considerado ao preparar um deployment futuro.

## Envio e identidades

1. A sessão determina User e Member; valores de autoria enviados pelo navegador não são usados.
2. O backend verifica atribuição, estado da conversa, canal e capabilities.
3. `operationId` é o `clientRequestId` do contrato existente: UUID de uma tentativa lógica, único por canal. Repetições recuperam a tentativa sem redisparo; conteúdo/autoria/destino incompatíveis produzem 409.
4. A tentativa PENDING é inserida antes de chamar o provider.
5. O adapter retorna ID remoto e IDs de fila/resposta separados. HTTP aceito não significa entrega.
6. Timeout, resposta inválida ou 5xx incerto resultam em UNKNOWN; não há retry de envio automático.
7. Callbacks reconciliam status sem regressão. A mensagem também expõe versão monotônica para o frontend preservar edições diante de frames atrasados, inclusive no mesmo milissegundo. Tentativas PENDING com mais de dois minutos viram UNKNOWN pelo reconciliador, sem novo envio.

`Message.id` é interno. `externalMessageId` é remoto; `providerTrackingId` guarda `zaapId`; `providerResponseId` preserva `id`. A documentação Z-API descreve `id` como compatibilidade do `messageId`; o modelo mantém os campos separados.

O contato é resolvido por canal + telefone normalizado. Um telefone em canais diferentes não é fundido. LID recebido junto de telefone é preservado como identidade externa na mensagem/envelope. **LID sem telefone E.164 não é convertido por suposição**: o callback fica pendente/falho para diagnóstico. Não há consulta LID→telefone inventada.

## Recebimento e webhooks

Z-API autentica chamadas externas com Client-Token. Esse header não é presumido no callback. A WXN utiliza um segredo próprio na query `secret`, configurado explicitamente nas URLs de callback.

POSTs receptores:

| Receptor                               | Finalidade                                         |
| -------------------------------------- | -------------------------------------------------- |
| `/api/webhooks/zapi/received`          | texto, fromMe, edição e reação documentados        |
| `/api/webhooks/zapi/received-delivery` | alias compatível de recebimento/fromMe             |
| `/api/webhooks/zapi/sent`              | callback de envio; preservado para URLs anteriores |
| `/api/webhooks/zapi/delivery`          | alias de envio                                     |
| `/api/webhooks/zapi/message-status`    | atualização por IDs                                |
| `/api/webhooks/zapi/connected`         | confirmação de conexão                             |
| `/api/webhooks/zapi/disconnected`      | confirmação de desconexão                          |

Fluxo: autenticação constante → canal/instância validados → limite de payload → fingerprint → envelope MongoDB → processamento → domínio → eventos autorizados. O provider só recebe sucesso depois da persistência do envelope. Falhas posteriores permanecem no diagnóstico, não são marcadas como processadas.

`webhook_inbox` usa `_id` derivado de canal, tipo e JSON canônico. Estados PENDING/PROCESSING/FAILED/PROCESSED; até cinco tentativas, intervalo mínimo de 30 segundos e lease de dois minutos. Scheduler consulta lotes de 25. ADMIN pode solicitar nova rodada em callbacks FAILED. Payload é removido após sucesso/classificação; envelopes expiram após sete dias. Metadados de conteúdo não suportado são classificados como `UNSUPPORTED_EXTERNAL_EVENT`; mídia não é baixada nem convertida em texto vazio.

Há limite de 256 KiB antes do parsing, 6.000 requisições/minuto por IP e 3.000 callbacks autenticados/minuto para a configuração atual. A tabela de IPs é limitada. A implantação pública ainda deve configurar limites de conexão e redação de query strings no proxy; não registre a URL completa de callback.

Recebimento individual encontra a conversa estável por canal + identidade. Novas conversas sem responsável ficam disponíveis somente para ADMIN. Retorno em WAITING_CUSTOMER ativa o atendimento; mensagens novas em RESOLVED/CLOSED reabrem em triagem sem responsável, preservando ciclos. Mensagens anteriores à conclusão não reabrem o ciclo.

## FromMe e reconciliação

- Mensagem já correlacionada: atualiza status, sem nova inserção.
- `fromApi=true` ou origem ambígua durante tentativa pendente: o ID é preservado nos logs; o envelope aguarda correlação em vez de criar duplicata por texto.
- Mensagem do aplicativo com origem externa conhecida: persiste OUTBOUND sem inventar autoria de um atendente; a Inbox mostra “Enviada fora da WXN”.
- Callbacks anteriores à resposta HTTP são guardados e reaplicados. `zaapId` permite associar delivery recebido antes da resposta.
- O reconciliador consulta até 100 tentativas por lote, com espaçamento de cinco minutos por tentativa; nunca reenvia texto.

Sem ID/fila verificável, não existe deduplicação automática segura por conteúdo. Em Configurações > Canais há reconciliação administrativa explícita: informar ID interno e ID remoto já observado. O servidor exige evidência de eco autenticado no mesmo canal, com destinatário e conteúdo compatíveis. Um ID já pertencente a outra mensagem não pode ser apropriado. Isto não prova que dois textos iguais sejam a mesma mensagem: a escolha continua humana e explícita.

## Ações de mensagem

| Ação                | Política                                                                                             |
| ------------------- | ---------------------------------------------------------------------------------------------------- |
| Reply               | mesma conversa/canal; ID remoto obrigatório; citação interna preservada                              |
| Forward nativo      | origem e destino autorizados, abertos e no mesmo canal/provider                                      |
| Editar              | somente OUTBOUND elegível; intenção e conteúdo anterior auditados                                    |
| Reagir/remover      | reação do número conectado, não uma reação privada de cada atendente                                 |
| Exclusão para todos | owner calculado no backend; intenção persistida e tombstone; conteúdo não é retornado no DTO apagado |
| Leitura remota      | política do canal habilitada por ADMIN + ação explícita por mensagem INBOUND                         |
| Copiar/reutilizar   | Clipboard com fallback / inserção no rascunho, sem envio automático                                  |

No Mock, mutações são confirmadas imediatamente. Na Z-API, edição e reação ficam ACCEPTED até callback correspondente; UNKNOWN mantém o conteúdo confirmado anterior e impede repetição cega. A Inbox apresenta esse estado. Operações incertas sem callback continuam exigindo inspeção externa; não há endpoint documentado de consulta de mensagem individual usado para inventar confirmação.

A leitura interna continua individual por Member. Kanban e Dashboard não chamam leitura remota. Mensagem pendente/falha não encerra o relógio de espera da equipe; notas e mensagens internas também não.

Não foi inventado um callback de exclusão remota: eventos não documentados permanecem classificados. A exclusão solicitada pela WXN segue a confirmação documentada `{value:true}` da operação. Limitações de elegibilidade impostas pela instância podem retornar erro controlado, sem apagar o registro local.

## Configuração Z-API

```dotenv
ZAPI_BASE_URL=https://api.z-api.io/instances/INSTANCE_ID/token/INSTANCE_TOKEN
ZAPI_CLIENT_TOKEN=ACCOUNT_SECURITY_TOKEN
ZAPI_CHANNEL_ID=ID_INTERNO_DO_CANAL
ZAPI_WEBHOOK_SECRET=SEGREDO_ALEATORIO_FORTE_COM_PELO_MENOS_32_CARACTERES
ZAPI_WEBHOOK_PUBLIC_BASE_URL=https://SEU_DOMINIO_PUBLICO
```

Os exemplos são placeholders; não configure domínio fictício como callback. `ZAPI_PUBLIC_BASE_URL` anterior continua como fallback. `ZAPI_WEBHOOK_SECRET_PREVIOUS` é opcional para uma janela de rotação e deve ser removido após atualizar os callbacks.

O servidor aceita apenas HTTPS em `api.z-api.io`, sem porta arbitrária, query, fragment ou credenciais no host. `externalChannelId` precisa corresponder à instância e `ZAPI_CHANNEL_ID` ao canal cadastrado. Nenhum navegador pode cadastrar URL/token de provider.

A referência segura atual é o slot de ambiente da primeira instância Z-API, ligado explicitamente a um canal. Canais Mock/Twilio simultâneos são independentes. **Uma segunda instância Z-API ainda exige outro slot de configuração no adapter**; ela não reutiliza silenciosamente as credenciais da primeira.

Em Configurações > Canais:

1. Cadastre um canal ZAPI com identificador de instância e número esperado.
2. Configure as envs e reinicie o backend.
3. Consulte conexão sem envio. Credenciais presentes não significam CONNECTED.
4. Obtenha QR Code quando aplicável. Imagem fica apenas na memória; quatro consultas de status a cada 15 segundos, interrompidas ao fechar ou conectar.
5. Se houver Challenge, conclua pelo painel Z-API. A WXN não tenta renderizá-lo como QR nem declara pareamento concluído.
6. Cadastre callbacks somente com HTTPS público real disponível. A configuração faz PUTs remotos e registra resultado administrativo; falha parcial exige nova inspeção.
7. Confirme chegada de callback autenticado. A tela mostra “Não verificado” até existir evidência.

Reinício/desconexão são POSTs internos ADMIN + CSRF + confirmação, mesmo que a operação remota seja GET. Não há reinício automático. A tela registra tentativas desconhecidas/falhas, intenções de mutação e callbacks pendentes. O estado consultado é uma observação temporal, não uma garantia eterna.

## Contratos oficiais consultados

Consultados em 31/10/2026; verificação local não equivale a conta real homologada.

- [Texto e edição](https://developer.z-api.io/message/send-text), [reply](https://developer.z-api.io/message/reply-message), [forward](https://developer.z-api.io/message/forward-message).
- [Reagir](https://developer.z-api.io/message/send-message-reaction), [remover reação](https://developer.z-api.io/message/send-remove-reaction), [ler](https://developer.z-api.io/message/read-message), [excluir](https://developer.z-api.io/message/delete-message).
- [Status](https://developer.z-api.io/instance/status), [QR imagem e Challenge](https://developer.z-api.io/instance/qr-code-image).
- [Recebimento](https://developer.z-api.io/webhooks/on-message-received), [exemplos de callback](https://developer.z-api.io/webhooks/on-message-received-examples), [notifySentByMe](https://developer.z-api.io/webhooks/update-notify-sent-by-me), [conectar](https://developer.z-api.io/webhooks/on-webhook-connected), [desconectar](https://developer.z-api.io/webhooks/on-whatsapp-disconnected).

`ZApiClient` concentra paths, header, serialização, timeout de conexão de 5s/leitura de 20s, limite de resposta de 1 MiB, ausência de redirect e erros sanitizados. Não guarda causas/corpos externos em exceções.

## Validação local e operação

```bash
npm run lint
mvn -DskipTests package
npm run build
git diff --check
mvn test
```

As fixtures HTTP de `ZApiContractTest` usam `MockRestServiceServer` e não fazem conexão de rede. Cobrem métodos, paths, Client-Token, JSON, queries, erros e separação de IDs. `ZApiWebhookTest`, `MessageReleaseRegressionTest` e os testes STOMP cobrem pontos críticos isolados.

Se o ambiente bloquear autoattach do Mockito, o Maven aceita `-DargLine=-javaagent:/CAMINHO/mockito-core-5.17.0.jar`. `scripts/verify-queue.sh` já aceita `MOCKITO_AGENT_JAR` e usa portas 14003/14004 e volumes de verificação separados; inspecione a configuração antes de executá-lo. Seus testes de fila limpam dados do banco de verificação. **Nunca habilite WXN_INTEGRATION_TEST apontando para dados de desenvolvimento/produção que deseja preservar.**

Os testes integrados são opt-in e precisam de PostgreSQL/MongoDB acessíveis. Nesta sessão as conexões locais e Docker foram recusados; migrations, REST/STOMP real e UI não foram homologados aqui.

## Iniciar a aplicação local

Na raiz, em ambiente local conhecido:

```bash
set -a
source .env
set +a
docker compose up -d
mvn spring-boot:run
```

O Flyway aplica V11 na inicialização sem reset. Em outro terminal:

```bash
npm --prefix frontend run dev -- --host localhost --port 4002
```

Use as contas já existentes. `npm run seed:demo -- --verify-idempotency` continua opcional e exige as variáveis locais documentadas no relatório de product polish; esta tarefa não executou seed nem alterou senhas.

## Roteiro Mock reproduzível

1. Inicie bancos, backend dev e frontend, mantendo as contas locais existentes.
2. ADMIN abre Configurações > Canais e cria MOCK com número `+12025550190`.
3. No diagnóstico, use “Simulador local” com contato `+12025550100` e texto; repita o mesmo callback e verifique uma única mensagem.
4. Abra Inbox/Kanban, atribua ao HUMAN de um ATTENDANT e entre com essa conta em outra sessão. Confirme bloqueio a conversas alheias.
5. Envie texto, reply, edição, reação/remoção, encaminhamento para outro atendimento no mesmo canal e exclusão. Confira histórico, tombstone e rascunho.
6. Em `/api/dev/whatsapp/mock/status`, POST com `channelId`, `externalMessageId`, `status`: READ, depois DELIVERED, depois READ. O estado final continua READ e logs de status não duplicam.
7. Habilite leitura remota no diagnóstico e use ação explícita da mensagem. Desabilite e confira que a ação deixa de estar disponível após consultar novamente as capacidades.
8. Simule fromMe do aplicativo: autoria “fora da WXN”, sem atribuição a um User aleatório.
9. Configure próximo envio TIMEOUT; envie pelo chat, confira UNKNOWN. Simule eco da API (fromMe + opção de correlação) com mesmo texto/contato e um ID remoto novo; use reconciliação explícita no diagnóstico. Não deve surgir uma segunda mensagem canônica.
10. Transfira; o responsável anterior perde REST/eventos, o novo recebe conversa e notificação idempotente. Atualize navegador e confirme persistência.
11. Desconecte/reinicie Mock com confirmação; consulte o estado na Inbox. Não há chamada externa. A simulação de estado/cenário do Mock é em memória do processo e volta ao padrão no reinício da aplicação.
12. Resolva/encerre e simule nova mensagem; confira novo ciclo e histórico anterior. Crie nota/mensagem interna e confirme que nenhum envio externo foi criado.

`LocalFlowIntegrationTest.mockReleaseActionsAndUnknownReconciliationUseCanonicalPersistence` automatiza uma parte desse roteiro quando o opt-in integrado estiver disponível. Navegação, scroll, pareamento real e troca de perfis em navegador ainda requerem homologação visual/operacional.

## Troubleshooting

| Situação                                 | Ação                                                                             |
| ---------------------------------------- | -------------------------------------------------------------------------------- |
| ECONNREFUSED ou permissão local recusada | verificar processos/portas e permissões do ambiente; não resetar bancos          |
| NOT_CONFIGURED                           | conferir slot do canal, instância, formato HTTPS e Client-Token                  |
| UNKNOWN no envio                         | não reenviar automaticamente; acompanhar callback e evidência de correlação      |
| Callback 403                             | conferir segredo, instância e canal; não liberar a API inteira                   |
| Callback 413/429                         | revisar volume/tamanho; provider deve repetir depois do limite                   |
| Callback FAILED                          | consultar metadados no diagnóstico, corrigir causa e solicitar reprocessamento   |
| Ação ACCEPTED sem confirmação            | validar callbacks do provider; conteúdo confirmado anterior permanece            |
| Erro 409 de atendimento                  | outra gravação ou transferência ocorreu; reconciliar/recarregar antes de repetir |
| QR Challenge                             | concluir autenticação adicional no painel do provider                            |
| ATTENDANT recebe 403                     | conferir conta, Member HUMAN ativo e assignedMemberId atual                      |

Não use logs com tokens, cookies, QR Code ou URLs completas da instância/callback. Não envie WhatsApp real sem autorização, número consentido e ambiente de integração separado.
