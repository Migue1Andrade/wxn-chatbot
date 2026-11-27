# WXN Chatbot

Aplicação acadêmica de atendimento WhatsApp para uma organização única. Backend Java 21/Spring Boot,
PostgreSQL com JPA/Flyway, MongoDB para dados conversacionais, AngularJS 1.8.3 e Tailwind CSS.

O estado arquitetural atual é single-organization. As regras de `companyId` em [AGENTS.md](AGENTS.md)
reservam uma evolução futura, mas não representam isolamento multi-tenant concluído. Consulte a
[arquitetura](docs/ARCHITECTURE.md) antes de alterar módulos ou contratos.

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

A sidebar é compartilhada entre todas as páginas. A página inicial é o chat. O painel `#!/dashboard`
exibe indicadores, o volume de entradas e resoluções dos últimos sete dias, o tempo de espera
por resposta e atalhos para a fila do Kanban. `#!/members` gerencia atendentes. `#!/settings` contém somente o
card Setores; a gestão fica em `#!/settings/departments`, com retorno ao hub.
Atendentes HUMAN exigem pelo menos um setor; BOT não recebe setores. Veja [setores](docs/departments.md).

Na tela Conversas, atendentes veem apenas as conversas atribuídas à própria conta. Administradores
alternam entre **Todas as conversas** e **Minhas conversas** e podem filtrar a visão geral por
responsável. Nas duas abas do administrador, conversas **Em atendimento** destacam quem está atendendo.
Em **Em atendimento**, a interface indica quem deve responder pela última mensagem registrada.
Encerramentos por inatividade aparecem em **Encerradas** junto dos demais encerramentos.
No Kanban, o painel de espera destaca até três conversas abertas que aguardam resposta há mais tempo,
com sinalização conforme o SLA configurado.

No chat e no Kanban, o responsável pode resolver ou encerrar um atendimento ativo e reabrir
seus atendimentos resolvidos ou encerrados manualmente. Sessões finalizadas pelo BOT ficam no
histórico. Administradores podem acompanhar e atribuir conversas. Alterações de status exigem
assumir a conversa.

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

Mock simula envio sem contatar um número real. O endpoint exige os perfis `dev,legacy`,
uma sessão de administrador e um canal `MOCK`. Exemplo de entrada:

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

## Base de Conhecimento do AutoGuia

Em **Configurações → Base de Conhecimento**, administradores podem criar e revisar artigos,
acompanhar a indexação, publicar, arquivar e transformar dúvidas sem cobertura em rascunhos.
Uma revisão em rascunho mantém a versão anterior disponível. Após a indexação, a nova versão
passa a ser a fonte consultada. O arquivamento remove o artigo da busca.

Em **Testar busca**, informe uma pergunta para ver os artigos, as versões e os trechos
encontrados. **Gerar resposta (prévia)** é uma ação separada, disponível quando houver fontes
e o gerador estiver configurado. A prévia não envia mensagens. Quando o RAG está habilitado
no atendimento, a falta de fontes encaminha o caso à equipe.

### Base inicial da WXN Tecnologia

Foram cadastrados 10 artigos em português no PostgreSQL local usado pelo AutoGuia. Na carga
inicial, todos estavam publicados na versão 1, com um trecho por artigo e um vetor de 768
dimensões gerado pelo EmbeddingGemma via Ollama. A carga foi feita no banco local e não é
executada automaticamente pelas migrations.

| Contexto               | Artigos publicados                                                                                                         |
| ---------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Prospecção e orçamento | Software web e mobile sob medida, sistema SaaS, contato comercial e diagnóstico                                            |
| Avaliação técnica      | Integração com ERP e APIs, infraestrutura em nuvem, implantação de sistemas, migração de plataforma, módulos customizáveis |
| Suporte                | Suporte especializado, consultoria, suporte e migração para TOTVS Protheus                                                 |

Cada artigo registra a fonte pública e inclui perguntas para ajudar na triagem. Preço,
prazo, condições comerciais e requisitos específicos ficam para confirmação com a equipe.
[Site oficial da WXN Tecnologia](https://www.wxntecnologia.com.br/)

### Validação da base inicial

As consultas textual e híbrida do aplicativo foram executadas diretamente no banco local.
A busca híbrida combinou texto e vetores com limiar de similaridade de `0,25`.

| Cenário                                                      | Busca textual | Busca híbrida |
| ------------------------------------------------------------ | ------------: | ------------: |
| Consultas diretas com a fonte correta em primeiro lugar      |         10/10 |         10/10 |
| Perguntas reformuladas com a fonte correta em primeiro lugar |           0/2 |           1/2 |
| Perguntas sem cobertura em que a busca retornou zero fontes  |           2/2 |           0/2 |

A busca híbrida encontrou o artigo certo para uma pergunta reformulada sobre migração, mas
confundiu integração com migração em outra pergunta. Também retornou artigos para duas
dúvidas sem cobertura, sobre preço mensal de hospedagem e venda de pneus. Esse teste mediu
a recuperação das fontes. A geração de respostas e o envio pelo WhatsApp ficaram fora da
validação da base inicial.

### Configuração e estado da ativação

As opções de RAG, embeddings, indexação e geração vêm desabilitadas por padrão no
[`.env.example`](.env.example). Com RAG habilitado, o atendimento do AutoGuia se apresenta
como **WXN Tecnologia**. Antes de liberar uma resposta, o gerador precisa selecionar frases
que respondam à pergunta e que constem literalmente em um dos trechos recuperados. O
backend envia o trecho aprovado diretamente ou encaminha o atendimento à equipe. Se o gerador estiver
indisponível, faltar evidência ou a saída contiver texto inventado, o atendimento segue
para a equipe.

O atendimento automático com RAG segue desativado no ambiente principal. Consulte os resultados do
teste com o gerador real no guia de avaliação antes da ativação.

Consulte o [guia de avaliação](docs/autoguia-rag-evaluation.md) para reproduzir os testes em
ambiente isolado e o [plano do RAG](docs/autoguia-rag-plan.md) para ver o desenho do fluxo
editorial e da integração com o AutoGuia.

## Qualidade

```bash
npm run lint:fix
npm run lint
npm run test
npm run build
npm run build:backend
mvn test
git diff --check
```

`npm run check` executa a sequência completa. Para verificações específicas também existem
`npm --prefix frontend test`,
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
O fluxo de redefinição de senha, SMTP e Mailpit local está em [recuperação de senha](docs/password-reset.md).

## WhatsApp real com Evolution API

A integração atual utiliza Evolution API `v2.3.7` com Baileys. A referência operacional,
incluindo Docker, variáveis, webhook, QR Code, reconexão e limpeza local, está em
[docs/whatsapp/evolution.md](docs/whatsapp/evolution.md).

O piloto WXN Carros pode encaminhar conversas com BOT para a fila humana. A solicitação de
atendimento cria alertas genéricos no sino; um atendente ou ADMIN com membro HUMAN ativo pode
reivindicar pelo `POST /api/notifications/{id}/claim` (sessão + CSRF). A primeira atribuição vence,
os outros alertas somem e apenas o responsável/ADMIN acessa o conteúdo. “Ler todas” preserva uma
oferta disponível. Consulte a documentação Evolution para configuração e teste com canal MOCK.

Atividades inclui Follow-ups vinculados a conversas e atribuídos por padrão ao criador. A API usa
`POST /api/tasks` e `PATCH /api/tasks/{id}`; a listagem aceita `type=FOLLOW_UP`, `period=OVERDUE|TODAY|UPCOMING`,
`status` e `page`. Os períodos usam o horário local de Recife e ordenam pelo prazo. O lembrete de vencimento
é idempotente por prazo, inclui o título do retorno no sino e abre a conversa original conforme permissões.
Reagendar ou concluir baixa o aviso pendente sem alterar status da conversa ou enviar WhatsApp.

Com o piloto habilitado, sessões BOT avisam após 20 minutos sem nova mensagem e encerram como `CANCELLED`
após 30 minutos. Respostas do agente podem definir `askResolution:true`; o backend só marca `RESOLVED` após
confirmação positiva do cliente. O encerramento expõe `botSessionEndedAt` e permite uma nova conversa isolada
para o mesmo canal e remetente. Consulte a [documentação Evolution](docs/whatsapp/evolution.md) para o callback
e as limitações de armazenamento em memória.

O fluxo real é `WhatsApp -> Evolution API -> Spring Boot -> MongoDB -> WebSocket -> AngularJS`.
O QR Code é obtido do provider e nunca é simulado. As implementações antigas de Mock, Twilio e
Z-API permanecem apenas para compatibilidade de testes/legado até a remoção definitiva de seus
contratos, mas não fazem parte do onboarding nem da configuração ativa. Mensagens recebidas de
qualquer contato válido são aceitas; a autenticação do webhook e a idempotência continuam ativas.

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

Edição, exclusão e outras mutações não misturam estado de entrega com estado da operação. Callbacks
confirmam a alteração quando disponíveis; quando uma confirmação não chega em até 120 segundos, a
operação é marcada como `TIMEOUT` e a interface orienta verificar o WhatsApp antes de repetir. Isso
evita loading infinito sem afirmar falsamente que a ação remota falhou ou foi concluída.

## Links Importantes

* [Google Sites do Projeto](https://sites.google.com/cesar.school/p3g15?usp=sharing) — Acompanhamento das entregas, protótipos e documentação visual do projeto.
* [Declaração de Uso de IA]() — Documento detalhando as ferramentas de Inteligência Artificial utilizadas no desenvolvimento.

## Equipe

| Integrante | | |
| :--- | :--- | :--- |
| João Pedro dos Santos Sousa  - jpss2@cesar.school|
| Luis Filipe Alves Silva Santos  - lfass@cesar.school |
| Lucas Samuel Pereira Alves  - lspa@cesar.school|
| Matheus Costa da Rocha  - mcr@cesar.school|
| Miguel josé França de Andrade - mjfa@cesar.school|
| Pedro Henrique Ramos da Silva - phrs@cesar.school|
| Vitória Gabrielle Janeiro Duran - vgjda2@cesar.school|
