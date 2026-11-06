# Evolução da plataforma — 30/10/2026

## Estado da entrega

**Esta implementação é parcial e não satisfaz integralmente o master prompt.** O código abaixo foi implementado, compilado e verificado nos limites descritos; os cenários integrados com PostgreSQL, MongoDB, navegador e WebSocket real não foram executados neste ambiente. Não considerar todos os critérios de aceite aprovados.

A base continua um monólito single-organization, Java/Spring Boot + PostgreSQL/Flyway + MongoDB + AngularJS/Vite. Não foram introduzidos tenant, companyId, infraestrutura externa, dependências de runtime, commits, push ou deploy. AGENTS.md foi preservado. O workspace já continha alterações de tarefas anteriores; elas não foram descartadas.

## Funcionalidades implementadas nesta iteração

| Área              | Implementação                                                                                                                                                                                                                                                                                                        |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Autorização       | Listas REST do atendente limitadas ao próprio Member; verificação de atribuição nas rotas de detalhes, mensagens e eventos; IDs diretos de mensagens também verificados.                                                                                                                                             |
| WebSocket         | Tópico global exclusivo ADMIN; tópico operacional por Member; revalidação de sessão e atribuição antes da entrega; evento de revogação ao responsável anterior; tópico individual de notificações.                                                                                                                   |
| Inbox             | Seleção preservada no query param `conversation`, restauração após refresh, painel recolhível de colaboração e acesso em telas compactas.                                                                                                                                                                            |
| Estados           | PENDING, IN_PROGRESS, WAITING_CUSTOMER, RESOLVED, CLOSED. Mudanças passam pelo serviço de domínio com CAS/version e eventos operacionais.                                                                                                                                                                            |
| Ciclos            | Timestamp de início para conversas novas; resolução conserva ciclos anteriores; reabertura inicia novo ciclo e incrementa contagem. Não se fabrica início de ciclo para legado sem timestamp.                                                                                                                        |
| Priorização       | LOW, NORMAL, HIGH, URGENT; alteração administrativa versionada com histórico.                                                                                                                                                                                                                                        |
| Kanban            | Rota administrativa `/kanban`, cinco colunas por status, 25 cards por página/coluna, carregamento incremental, busca, prioridade, responsável, não atribuídos, filtros de SLA, ordenação, contadores, drawer, atribuição/transferência direta administrativa e movimentação de status por drag ou seletor acessível. |
| Visualizações     | Configuração de filtros e apresentação salva no PostgreSQL por administrador; criação, escolha e exclusão.                                                                                                                                                                                                           |
| Notas             | Criação, edição, exclusão lógica, fixação, histórico de alterações e listagem paginada autorizada.                                                                                                                                                                                                                   |
| Discussão interna | Persistência própria no MongoDB, autoria real, edição/exclusão pelo autor, paginação e atualização por evento. Nenhuma chamada ao WhatsAppProvider.                                                                                                                                                                  |
| Rascunhos         | Persistência por usuário/conversa/modo EXTERNAL ou INTERNAL. Debounce, gravações sequenciais e limpeza após sucesso; a autorização é reavaliada no servidor.                                                                                                                                                         |
| Respostas rápidas | Templates pessoais e globais no PostgreSQL; criação, edição, duplicação na UI, inativação, exclusão lógica e controle de versão. Globais somente ADMIN.                                                                                                                                                              |
| @@                | Autocomplete no compositor com navegação por teclado; resolução por whitelist no backend; inserção editável; variáveis ausentes informadas; texto com variável não resolvida bloqueado no envio e na resposta. Não há envio automático nem eval.                                                                     |
| Atividades        | TASK, FOLLOW_UP, REMINDER persistidos no PostgreSQL; criação, edição, conclusão, cancelamento, filtro por estado, paginação e ordenação pelo prazo. Atendente gerencia as próprias; ADMIN gerencia a operação.                                                                                                       |
| Notificações      | Persistência individual, sino com contador, página, marcar uma/todas lidas. Atribuição e vencimento de tarefas geram notificações, publicadas após commit.                                                                                                                                                           |
| Agendamento       | Verificação de até 100 tarefas vencidas por minuto, com trava e marcador de notificação para impedir repetição contínua.                                                                                                                                                                                             |
| SLA               | Política única desabilitada por padrão; primeira resposta, subsequente, resolução, percentual de aviso e timezone; cálculo no backend e filtros no MongoDB.                                                                                                                                                          |
| Dashboard         | Métricas reais compartilhadas com Kanban e gráfico por status. Estado sem dados e médias indisponíveis são explícitos.                                                                                                                                                                                               |
| Relatórios        | CSV administrativo de atendimentos, gerado em páginas de 100 registros, com células escapadas e proteção de fórmulas.                                                                                                                                                                                                |

## Contratos REST adicionados

Todos os caminhos começam com `/api` e continuam sujeitos à sessão, chave dev e CSRF existentes quando aplicável.

- `GET /admin/kanban` e `/admin/kanban/conversations`: cards paginados.
- `GET /admin/kanban/metrics`, `/admin/kanban/groups`.
- `GET /admin/kanban/conversations/{id}`.
- `PATCH /admin/kanban/conversations/{id}/priority`.
- `GET/POST /admin/kanban/views`; `PATCH/DELETE /admin/kanban/views/{id}`.
- `PATCH /conversations/{id}/status?status=RESOLVED`: mesma aplicação de domínio da Inbox, com ator autenticado e versão esperada.
- `GET /conversations/{id}/events?order=DESC`: histórico recente sem leitura remota.
- `GET/POST /conversations/{id}/notes`.
- `PATCH/DELETE /notes/{id}`; `GET /notes/{id}/history`.
- `GET/POST /conversations/{id}/internal-messages`.
- `PATCH/DELETE /internal-messages/{id}`; `GET /internal-messages/{id}/history`.
- `GET /conversations/{id}/draft?mode=EXTERNAL`; `PUT /conversations/{id}/draft`.
- `GET/POST /quick-replies`; `PATCH/DELETE /quick-replies/{id}`.
- `POST /quick-replies/preview`.
- `GET/PUT /admin/settings/sla`.
- `GET /admin/analytics/overview`; `GET /admin/analytics/conversations.csv`.
- `GET/POST /tasks`; `PATCH /tasks/{id}`.
- `GET /notifications`, `/notifications/unread-count`.
- `PATCH /notifications/{id}/read`; `POST /notifications/read-all`.

Rotas `/api/admin/**` rejeitam ATTENDANT. Alterações de atribuição, transferência direta e assunção foram restringidas a ADMIN. O atendente continua podendo responder e alterar estados das próprias conversas. Uma tarefa ou notificação não concede acesso ao chat.

## Eventos e canais

Foram preservados os eventos canônicos anteriores e adicionados:

- `conversation.access.revoked`: contém somente identificação do recurso e Member que perdeu acesso.
- `conversation.team.updated`: invalidação para recarregar colaboração autorizada, sem conteúdo interno no payload.
- `kanban.metrics.updated`: invalidação administrativa por minuto quando SLA está habilitado; sem transmissão do quadro inteiro.
- `notification.created`: aviso individual para recarregar a própria central.

Destinos:

- `/topic/conversations`: ADMIN.
- `/topic/members/{memberId}/conversations`: somente Member do atendente autenticado.
- `/topic/users/{userId}/notifications`: somente usuário autenticado correspondente.

A atribuição é reconsultada no canal de saída, impedindo entrega de eventos de chat que ficaram em fila após transferência. Reconexão exige novamente a sessão válida. A Inbox remove a conversa e desfaz a seleção ao receber revogação.

## SLA e métricas

Política inicial: desabilitada. Os valores 30/30/1440 minutos e aviso em 80% são defaults editáveis, não indicadores calculados nem prazos impostos até habilitar a política.

- Prazo de resposta = `waitingForAgentSince + limiteDeResposta`.
- Sem saída anterior registrada, usa `firstResponseMinutes`; caso contrário, `nextResponseMinutes`.
- Prazo de resolução = `cycleStartedAt + resolutionMinutes`.
- Aviso = início + limite × `warningThresholdPercent / 100`.
- `BREACHED`: algum prazo aplicável expirou.
- `AT_RISK`: algum aviso foi alcançado sem violação.
- `ON_TRACK`: prazos aplicáveis ainda estão abaixo do aviso.
- `NOT_APPLICABLE`: timestamps insuficientes ou nenhuma espera/ciclo aplicável.
- `NOT_CONFIGURED`: política desabilitada.
- `COMPLETED`: conversa finalizada; não significa certificação retrospectiva de cumprimento do SLA.

São **minutos corridos**, sem calendário de horários úteis. A resposta não corre em WAITING_CUSTOMER; o SLA de resolução continua. Mensagens internas não atualizam timestamps de resposta externa. A espera por resposta é atualizada atomicamente a partir dos eventos de mensagens, com comparação temporal para callbacks fora de ordem.

“Resolvidos hoje” considera `resolvedAt` no dia do timezone da política. Resolução média considera todos os ciclos registrados com timestamps de início/fim; é uma média histórica, não limitada ao dia. Ciclos anteriores ficam preservados no documento. Exportações não fornecem snapshot transacional da operação em movimento.

## Attention Score

Regra central atual, determinística:

- +100 SLA violado ou +50 SLA em risco, sem acumular os dois.
- +45 URGENT ou +25 HIGH, sem acumular os dois.
- +35 conversa aberta sem responsável.
- Conversas RESOLVED/CLOSED recebem zero.

O backend retorna as razões. O score **não altera a prioridade manual**. Ordenação ATTENTION usa score decrescente, timestamps de espera/abertura e `_id` como desempate. As alternativas são atividade recente e abertura mais antiga. A classificação ainda não incorpora handoff, solicitações de transferência, atividades vencidas ou categorias CRITICAL/HIGH/NORMAL/LOW derivadas.

## Migrations e persistência

Nenhuma migration anterior foi editada nesta iteração.

| Migration                              | Alteração                                                                |
| -------------------------------------- | ------------------------------------------------------------------------ |
| V6__create_kanban_views.sql            | Visualizações pessoais de administradores.                               |
| V7__create_quick_replies.sql           | Templates, unicidade de atalhos por escopo/usuário e controle de versão. |
| V8__create_sla_policy.sql              | Política única, desabilitada por padrão, com limites e timezone.         |
| V9__create_tasks_and_notifications.sql | Atividades e notificações individuais.                                   |

Novos modelos relacionais: KanbanView, QuickReply, SlaPolicy, WorkTask, UserNotification.
Novo documento: TeamEntry (com revisões). Rascunhos usam `conversation_drafts`. Conversation foi estendida com prioridade e timestamps operacionais; não há entidade paralela para cards. Índices adicionais são criados pelo runner de índices MongoDB já existente.

## Arquivos criados

Backend, sob `src/main/java/com/wxn/chatbot/`:

- `auth/service/ConversationAuthorization.java`.
- `conversation/event/AccessRevoked.java`.
- `kanban/{controller,dto,entity,repository,service}/`: controllers, DTOs, consultas, views e AttentionScoreService.
- `collaboration/{controller,document,dto,repository,service}/`: notas, mensagens internas e rascunhos.
- `quickreply/{controller,dto,entity,repository,service}/`.
- `sla/{controller,entity,repository,service}/`.
- `analytics/{controller,service}/`.
- `task/{controller,entity,repository,service}/`.
- `notification/{controller,entity,repository,service}/`.
- `config/SchedulingConfiguration.java`.
- Migrations V6 a V9 descritas acima.

Frontend, sob `frontend/src/features/`:

- `kanban/`: service, diretiva, template e estilos.
- `collaboration/`: painel, template, estilos e serviço de gravação sequencial de rascunhos.
- `quick-replies/`: diretiva e template.
- `dashboard/dashboard.directive.js`.
- `sla/`: diretiva e template.
- `reports/`: diretiva e template.
- `tasks/`: diretiva e template.
- `notifications/`: serviço, diretivas e templates separados.

Documentação: este relatório.

## Arquivos modificados

- Auth: AccountSessions, AccountAccessInterceptor, AccountSocketAccess e o serviço frontend de sessão (limpeza de dados privados ao trocar usuário/perfil).
- Conversas: Conversation, ConversationStatus, ConversationActivityType, ConversationController, ConversationService, ConversationManagementService, ConversationTransitionService, ConversationActivityService, ConversationMapper, ConversationOperations/Impl, ConversationActivityRepository e ConversationEventTypes.
- WebSocket: DevelopmentStompInterceptor e StompConversationEventBridge.
- Infra existente: MongoConfiguration.
- Mensagens: MessageActionsService (bloqueio de variáveis não resolvidas e de envios em RESOLVED).
- Frontend: app/main.js, app.routes.js, ApiClientService, SocketService, constantes de eventos, ChatsController, chat-layout.html, DevMemberContextService, compositor, navegação, hub de configurações e template do dashboard.
- package.json: script `build` na raiz.
- scripts/seed-chat-examples.mjs: ator da sessão e destinos Mock reservados; namespace v2 preserva exemplos anteriores.
- Dois testes existentes de WebSocket foram atualizados para o novo contrato e isolamento. Nenhuma nova suíte foi criada.
- README.md: instruções e ligação para este relatório.

Nenhum arquivo foi removido nesta iteração. Deleções já presentes no workspace de tarefas anteriores não são atribuídas a esta entrega.

## WhatsApp Strategy e Z-API

MockWhatsAppProvider, ZApiWhatsAppProvider, TwilioWhatsAppProvider e o resolver por canal existentes foram preservados. O Mock continua sem chamadas externas. Não foram ativadas capacidades que o adapter atual não implementa.

O adapter Z-API existente suporta envio, resposta citada, encaminhamento, exclusão e callbacks. **Não foram implementadas nesta iteração edição, reações, remoção de reações ou confirmação remota de leitura.** A UI deve continuar respeitando o contrato existente de capacidades.

A documentação consultada confirma `send-text`, `messageId` para respostas, `editMessageId` para edição, `forward-message`, `update-webhook-delivery` e `update-webhook-received-delivery`. Referências oficiais: [texto](https://developer.z-api.io/message/send-message-text), [recebimento](https://developer.z-api.io/webhooks/on-message-received), [envio](https://developer.z-api.io/webhooks/on-message-send). Isso não substitui teste com instância real. Nenhuma credencial foi usada para enviar WhatsApp, nenhuma URL pública foi cadastrada e não houve deploy.

## Limitações e critérios ainda não atendidos

Esta lista é parte do estado real da implementação:

1. Não há workflow REQUESTED/ACCEPTED/REJECTED/CANCELLED/COMPLETED de solicitação de transferência; existe transferência administrativa direta usando domínio anterior.
2. Não há HumanHandoffRequest nem cenários persistidos de solicitação humana.
3. Não há autocomplete de @menções, internal requests com resposta por destinatário não atribuído, nem notificações desses eventos.
4. Não há distribuição automática, capacidade por atendente ou presença operacional AVAILABLE/BUSY/AWAY/OFFLINE.
5. Não há novo módulo Contact, etiquetas, campos personalizados ou configuração completa da organização.
6. Preferências pessoais, tema, densidade, som, política de Enter, troca de senha pela nova tela e desempenho individual não foram implementados.
7. Kanban agrupa somente por status; não há swimlanes, agrupamento por atendente/setor/canal/SLA, colunas ocultas ou personalização de campos.
8. Views salvam filtros e apresentação. Renomear está disponível na API PATCH, mas sem UI própria; não há duplicação, densidade ou padrão compartilhado.
9. Drag mostra confirmação e usa CAS no servidor; não há movimento otimista com rollback visual. O card permanece no lugar até confirmação do servidor.
10. Filtros de período, etiquetas, canal na UI, espera mínima e solicitações não foram concluídos. Existem filtros de canal na API.
11. Dashboard não inclui séries por dia, produtividade, canal/setor, disponibilidade, volume de mensagens e todas as médias pedidas.
12. CSV exporta o histórico completo atual; não há conjunto completo de relatórios por período e dimensões operacionais.
13. SLA usa tempo corrido. Não há calendário de dias/horários úteis, pausa configurável da resolução, snapshots históricos da política ou notificações persistidas de violação.
14. O score ainda usa apenas prioridade, não atribuição e SLA. Não considera todas as regras propostas no master prompt.
15. Atividades geram notificações de atribuição/vencimento; as demais categorias pedidas ainda não têm produtores. Não há dropdown de notificações ou filtros completos.
16. Discussão interna tem edição/exclusão e histórico; não há resposta encadeada por ID, cópia dedicada ou integração de menções.
17. Templates pessoais/globais funcionam; a sugestão do compositor carrega a primeira página de 100 templates. Campos de contato/organização ausentes são reportados como variáveis sem valor. As variáveis de data/hora do preview usam UTC nesta versão.
18. Formulário de templates não tem teste de variáveis separado; a prévia ocorre ao inserir no compositor.
19. Não há seed completo dos quinze cenários do Kanban. O script existente cria exemplos Mock sem substituir contas ou apagar dados.
20. Não há autenticação Spring Security completa de produção adicionada. Foram preservadas sessões/CSRF e a proteção dev existentes, com reforço de autorização. A aplicação continua condicionada ao ambiente dev protegido já existente.
21. REST/STOMP e demonstração visual ainda precisam de validação integrada após iniciar os bancos e aplicar as migrations.

Não existem telas ou botões fingindo representar os módulos ausentes. A falta de configuração de SLA é explicitamente exibida.

## Verificações e impedimentos

- `npm run lint:fix`: executado, com revisão dos arquivos alterados.
- `npm run lint`: executado.
- `mvn -DskipTests package`: build backend executado.
- `npm run build`: build Vite executado.
- `npm --prefix frontend test`: 7 testes existentes passaram.
- `mvn test`: primeira execução bloqueada pelo auto-attach do Mockito no sandbox.
- Reexecução com agente Mockito explícito: 39 testes existentes, 0 falhas, 0 erros, 15 ignorados (integrações dependentes de ambiente); 24 executados.
- `git diff --check`: executado.
- Nenhuma nova suíte automatizada.
- `docker compose ps`: negado pelo sandbox ao acessar o socket do Docker; não foi possível aplicar V6–V9, iniciar com bancos, nem validar REST/STOMP/Mock ponta a ponta.
- Não havia ferramenta de navegador disponível para validar visualmente as novas telas. Build não equivale a validação visual.
- Não houve conexão real Z-API.

## Execução local

Na raiz, mantendo seu `.env` atual:

```bash
set -a
source .env
set +a

docker compose up -d
mvn spring-boot:run
```

Flyway aplicará somente V6–V9 se V1–V5 já corresponderem ao banco. Não executar repair/reset para contornar divergências de histórico. MongoDB recebe os índices do runner existente. O backend precisa ser reiniciado para carregar o novo código.

Em outro terminal:

```bash
npm --prefix frontend run dev -- --host localhost --port 4002
```

Acesse o login existente. Com ADMIN: `/dashboard`, `/kanban`, `/tasks`, `/notifications`, `/reports`, `/settings/sla` e `/settings/global-quick-replies`. Com ATTENDANT: Inbox própria, atividades, notificações e `/settings/quick-replies`. As tentativas administrativas devem ser verificadas também por REST com a sessão do atendente, não somente pela navegação.

Para exemplos opcionais, execute primeiro o seed local de contas já existente. Informe as credenciais de ADMIN apenas no seu terminal e execute `node scripts/seed-chat-examples.mjs`. Ele exige `WXN_SEED_USERNAME` e `WXN_SEED_PASSWORD`, sessão, CSRF, perfil dev e canal Mock. Não executa automaticamente no startup.

Para testar SLA sem alterar relógios nem inventar timestamps, habilite uma política curta em `/settings/sla`, receba uma mensagem Mock, mantenha a conversa aberta e aguarde o prazo. O sinal administrativo é reavaliado por minuto. Não existe simulador completo de handoff ou solicitação de transferência nesta entrega.

## Pendências antes da integração WhatsApp real

Validar o banco e todas as novas rotas com sessões reais; revisar a segurança fora do modo dev; completar as capacidades de edição/reação/leitura que forem oferecidas; testar os callbacks oficiais com uma instância autorizada e URL HTTPS real; validar autenticação e idempotência dos webhooks existentes. Nada disso foi declarado concluído.
