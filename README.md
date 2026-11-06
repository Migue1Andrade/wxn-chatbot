# WXN Chatbot

Aplicação acadêmica de atendimento WhatsApp para uma organização única. Backend Java 21/Spring Boot,
PostgreSQL com JPA/Flyway, MongoDB para dados conversacionais, AngularJS 1.8.3 e Tailwind CSS.

## Execução local

Configure `.env` a partir de `.env.example`, com credenciais locais e uma `DEV_API_KEY` de pelo menos
32 caracteres. `VITE_DEV_API_KEY` deve ter o mesmo valor; essa chave é exposta ao navegador no ambiente
de desenvolvimento. Não versione `.env`.

```bash
npm run dev:compose
```

Esse comando inicia PostgreSQL, MongoDB, Redis, Evolution API, backend e frontend.

Portas: backend 4001, frontend 4002, PostgreSQL 4003 e MongoDB 4004. Vite lê `.env` da raiz;
o backend recebe as variáveis exportadas. `SPRING_PROFILES_ACTIVE=dev` habilita a API interna e Mock;
ativar `prod` bloqueia esses controles de desenvolvimento. O cadastro e o login por sessão são descritos em [contas de usuário](docs/user-accounts.md).

## Bancos e migrations

V1 cria `users` e `members`, V2 cria `departments` e `member_departments`, V3 cria
`whatsapp_channels`. V4 acrescenta identidade e credenciais aos usuários existentes de forma incremental.
O Hibernate usa `ddl-auto=validate`. Esta entrega não reseta bancos nem altera V1–V3.

O histórico anterior à refatoração estrutural está documentado em
[relatório histórico](docs/single-organization-report.md). Ele não deve ser usado como instrução de reset
para o cadastro de contas atual. Não execute Flyway repair para aplicar V4.

O MongoDB mantém `conversations`, `messages`, `message_logs`, `conversation_events` e
`conversation_reads`, com `_id` nativo. O backend cria os índices na inicialização. Conversas usam
unicidade por canal e chave da conversa; mensagens usam canal e ID externo. Histórico, status,
idempotência, auditoria e leituras por atendente permanecem separados por suas entidades e canais.

## Interface

A sidebar é compartilhada entre todas as páginas. A página inicial é o chat, `#!/dashboard` apresenta
um aviso de implementação futura e `#!/members` gerencia atendentes. `#!/settings` contém somente o
card Setores; a gestão fica em `#!/settings/departments`, com retorno ao hub.
Atendentes HUMAN exigem pelo menos um setor; BOT não recebe setores. Veja [setores](docs/departments.md).

Para recuperar rapidamente o contexto das telas, preparar uma base completa e seguir um roteiro de
demonstração, consulte o [guia de contexto e demonstração](docs/feature-tour.md).

## API e eventos

Todas as rotas internas usam `X-Dev-Api-Key` e o prefixo `/api`:

| Métodos       | Caminho                                                                       |
| ------------- | ----------------------------------------------------------------------------- |
| POST, GET     | `/api/users`                                                                  |
| POST, GET     | `/api/members`                                                                |
| GET, PATCH    | `/api/members/{memberId}`                                                     |
| POST, GET     | `/api/departments`                                                            |
| PATCH, DELETE | `/api/departments/{id}`                                                       |
| POST, GET     | `/api/whatsapp/channels`                                                      |
| GET           | `/api/conversations`                                                          |
| GET           | `/api/conversations/{id}`                                                     |
| PATCH         | `/api/conversations/{id}/assume`, `/assign`, `/transfer`, `/close`, `/reopen` |
| GET           | `/api/conversations/{id}/events`                                              |
| POST          | `/api/conversations/{id}/read`                                                |
| GET, POST     | `/api/conversations/{id}/messages`                                            |
| POST          | `/api/dev/whatsapp/mock/inbound`                                              |

A listagem de conversas aceita `paged=true`, `page`, `size`, `status`, `assignedMemberId`, `unassigned`,
`readerMemberId` e `search`. Mensagens usam cursores `limit`/`before` ou paginação `page`/`size`.
O header de desenvolvimento não é exigido nos webhooks Twilio, que validam a assinatura do provider.

WebSocket: `ws://localhost:4001/ws`. CONNECT exige `X-Dev-Api-Key`; SUBSCRIBE permite apenas
`/topic/conversations`. SEND de clientes é bloqueado, pois mensagens são enviadas por REST.
Envelope: `eventId`, `type`, `conversationId`, `occurredAt`, `payload`. Os payloads são DTOs.

Mock simula envio sem contatar um número real. Exemplo de entrada:

```json
{
  "channelId": 1,
  "externalMessageId": "exemplo-1",
  "senderPhone": "+5581999999999",
  "content": "Olá"
}
```

O ID de canal deve existir e identificar um canal MOCK ativo com telefone configurado. Repetir o ID
externo no mesmo canal não duplica a mensagem. `node scripts/seed-chat-examples.mjs` cria exemplos
locais explicitamente, sem executar automaticamente no startup.

Twilio usa `TWILIO_CHANNEL_ID`, `TWILIO_CHANNEL_KEY`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`,
`TWILIO_PHONE_NUMBER` e `TWILIO_PUBLIC_BASE_URL`. Webhooks:
`/api/webhooks/whatsapp/twilio/{channelKey}` e o sufixo `/status`. O endereço público deve ser HTTPS,
sem barra final, e a conta, assinatura e telefone precisam corresponder ao canal configurado.

A coleção [Postman](postman/WXN-WhatsApp.postman_collection.json) usa os contratos atuais.

## Qualidade

```bash
npm run lint:fix
npm run lint
mvn -DskipTests package
npm --prefix frontend run build
git diff --check
```

Para verificações existentes: `mvn test`, `npm --prefix frontend test` e
`bash scripts/verify-queue.sh` (bancos exclusivos de verificação). Consulte
[estilo](docs/code-style.md) e [verificação da fila](docs/queue-verification.md).
Nenhuma suíte nova é necessária para executar os builds.

## Usuários e seed local

A tela de Profissionais usa um único formulário para ADMIN e ATTENDANT. CPF, username e e-mail são
obrigatórios e únicos, inclusive para contas inativas. Os registros antigos permanecem preservados e
precisam de conclusão cadastral antes do login. Não execute reset para aplicar V4.

Com o backend atualizado, execute manualmente `node scripts/seed-local-users.mjs` para criar as duas
contas de demonstração. O script preserva contas e senhas existentes e rejeita conflitos de identidade.
Consulte [cadastro, autenticação, permissões e validação](docs/user-accounts.md).

## WhatsApp real com Evolution API

A integração atual utiliza Evolution API `v2.3.7` com Baileys. A referência operacional,
incluindo Docker, variáveis, webhook, QR Code, reconexão e limpeza local, está em
[docs/whatsapp/evolution.md](docs/whatsapp/evolution.md).

O fluxo real é `WhatsApp -> Evolution API -> Spring Boot -> MongoDB -> WebSocket -> AngularJS`.
O QR Code é obtido do provider e nunca é simulado. As implementações antigas de Mock, Twilio e
Z-API permanecem apenas para compatibilidade de testes/legado até a remoção definitiva de seus
contratos, mas não fazem parte do onboarding nem da configuração ativa.

## Histórico de providers

A documentação histórica de Z-API, Twilio e Mock pode permanecer no repositório para rastrear decisões
acadêmicas anteriores, mas não é uma configuração suportada para uma instalação nova. O backend não expõe
essas opções no fluxo de onboarding.

## Evolução da plataforma de atendimento

O código inclui Kanban administrativo conectado às conversas, dashboard com métricas reais,
notas, discussão interna, rascunhos, respostas rápidas `@@`, atividades, notificações e política
de SLA. A entrega ainda **não cobre integralmente** o master prompt. O estado funcional,
contratos, migrations V6–V9, fórmulas, limitações e comandos locais estão no
[relatório de implementação da plataforma](docs/platform-implementation.md).

Após atualizar o código, reinicie o backend para aplicar as novas migrations incrementais.
Não apague dados nem execute repair para mascarar divergências de histórico.

```bash
npm run dev:compose
```

O frontend fica disponível em `http://localhost:4002` e o backend em `http://localhost:4001`.
O build da raiz agora pode ser executado com `npm run build`.

## Revisão visual e demonstração local — 31/10/2026

A navegação móvel, os overlays, o hub de Configurações e as tabelas de Usuários/Setores
foram padronizados. A migration incremental V10 adiciona apresentação/status de setores,
um manifesto de seed e Members HUMAN ausentes em administradores legados, sem alterar senhas.

O comando `npm run seed:demo -- --verify-idempotency` prepara registros fictícios para os
módulos existentes. Exige perfil dev, backend local, sessão ADMIN e as variáveis
`WXN_SEED_USERNAME`, `WXN_SEED_PASSWORD` e `WXN_DEMO_PASSWORD`. Não possui senha padrão.
Não envia mensagens reais nem altera configurações existentes.

**O seed e sua segunda execução ainda não foram validados contra os bancos neste ambiente.**
O acesso ao Docker foi bloqueado; a execução também exige credenciais locais não disponíveis
na sessão. Veja [instruções, inventário, volume previsto e auditoria](docs/product-polish-report.md).

## Fechamento do módulo WhatsApp TEXT-only

A referência operacional atual é [docs/whatsapp/evolution.md](docs/whatsapp/evolution.md). O guia antigo
[docs/whatsapp/README.md](docs/whatsapp/README.md) é histórico e contém contratos dos providers anteriores.

Configurações > Canais agora deve ser usada para diagnóstico do canal Evolution. A migration incremental V12
registra o estado do onboarding sem alterar migrations anteriores.
