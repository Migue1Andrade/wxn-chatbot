# WXN — revisão de produto e auditoria de 31/10/2026

## 1. Resumo e limites da validação

Foram implementadas melhorias na interface de todas as rotas existentes, no hub de Configurações, na navegação responsiva, nos overlays, nas tabelas de Usuários/Setores e no tratamento de estados assíncronos. A revisão corrigiu problemas de autoria operacional do ADMIN, consultas repetidas, paginação de templates, notificações e leitura de chats ocultos.

Foi criado um seed local para **os modelos que realmente existem**. Seu volume foi validado em memória, mas **não houve execução integrada nem confirmação de idempotência contra PostgreSQL/MongoDB**: o socket Docker foi negado e faltam credenciais locais para o comando. Não há contagens reais de tabelas/coleções a declarar. O comando as calcula quando executado com os bancos disponíveis.

O Chrome instalado abortou nas duas tentativas de execução headless, com saída 134. Portanto, a revisão de responsividade foi estática; não houve screenshots, testes visuais no navegador ou certificação WCAG. Não foram criados commits, push, deploy, reset de banco, novas suítes de testes ou dependências de runtime. AGENTS.md permaneceu idêntico ao início da tarefa.

## 2. Diagnóstico anterior

- Setores usava tabela, espaçamento e ações diferentes dos usuários; não havia busca, paginação nem apresentação móvel equivalente.
- Configurações era uma lista de links grandes, sem categorias, busca ou localização contextual.
- A sidebar ocupava largura permanente no mobile e os ícones não tinham rótulos visíveis no drawer.
- Diversos módulos tinham cores, fontes secundárias, headers e formulários desconectados.
- O Kanban móvel empilhava todas as colunas sem seleção de etapa.
- Overlays tinham implementações diferentes de foco e ESC; detalhes da Inbox não tinham comportamento completo de drawer.
- Algumas telas mostravam estado vazio durante carregamento ou falha, e faltavam ações de tentar novamente.
- O serviço de notificações podia disparar a mesma consulta duas vezes ao montar sino e página.
- Usuários, integrantes e setores tinham padrões de consulta por registro, ampliando o número de queries.
- ADMIN sem setor podia ficar sem Member HUMAN e ser impedido de registrar ações com a própria identidade.
- O chat podia considerar uma mensagem visível mesmo com seu painel oculto no mobile.

## 3. Inventário funcional confirmado

| Recurso                                                     | Estado encontrado e preservado                                                                 |
| ----------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| Login, sessões, CSRF e ADMIN/ATTENDANT                      | Implementados com a proteção dev existente; não foi introduzida autenticação nova de produção. |
| Inbox e mensagens TEXT                                      | Implementados, com paginação, eventos e ações dependentes de capacidades.                      |
| Notas, mensagens internas, rascunhos                        | Implementados; sem menções ou pedidos internos por destinatário.                               |
| Notificações                                                | Página, sino, leitura individual/todas; produtores de atribuição/vencimento de atividades.     |
| Templates e @@                                              | Persistidos, com escopos pessoal/global e preview de variáveis nativas.                        |
| Usuários, Members e setores                                 | Implementados; setores evoluídos nesta revisão.                                                |
| Atividades e lembretes                                      | Persistidos; conclusão, cancelamento, prazo e notificações.                                    |
| Dashboard e relatórios                                      | Métricas existentes e CSV; não há todos os relatórios do master prompt anterior.               |
| Kanban e SLA                                                | Implementados parcialmente no escopo anterior; esta revisão aprimora apresentação e navegação. |
| Canais e Strategy                                           | API e resolução por canal; não há página completa de gestão de canais.                         |
| Mock e Z-API                                                | Adapters existentes preservados. Nenhuma chamada Z-API real nesta tarefa.                      |
| Transferências                                              | Transferência direta administrativa e histórico; sem workflow de solicitações pendentes.       |
| Contatos independentes, etiquetas, campos personalizados    | Não implementados; não foram criadas telas ou tabelas fictícias.                               |
| Menções, internal requests e human handoff                  | Não implementados; excluídos explicitamente do seed.                                           |
| Distribuição automática, presença e perfil pessoal completo | Não implementados; não foram expostos como opções do hub.                                      |

## 4. Design System

`frontend/src/shared/styles/product.css` centraliza tokens de fundo, superfície, texto, borda, primária, sucesso, aviso, perigo, informação, espaçamentos, raios, sombras e transições. O estilo da tabela foi movido para `shared/styles/directory.css`, compartilhado pelos dois diretórios.

- Tema claro único, respeitando a identidade verde da WXN.
- Tipografia de página, seção, caption e labels consistente.
- Superfícies claras, bordas discretas e sombras curtas.
- Formulários com controles de altura consistente e foco visível.
- Estados selecionado, hover, disabled, loading, error e success.
- Badges sem dependência exclusiva de cor: status/prioridade têm texto.
- Tags de setor escurecem o texto quando necessário para manter contraste com o fundo calculado, sem alterar a cor cadastrada.
- Movimento reduzido respeitado por media query.

O contraste dos tokens foi calculado pela luminância relativa sRGB. Texto principal, secundário, primária e cores semânticas superam 4,5:1 contra branco. O token secundário mais claro foi escurecido durante a revisão. Essa verificação é específica das cores, não uma auditoria automatizada WCAG da aplicação inteira.

## 5. Páginas e responsividade

Todas as rotas registradas foram inspecionadas e receberam o padrão aplicável: login, Inbox, dashboard, Kanban, atividades, notificações, relatórios, usuários, setores, templates pessoais/globais, SLA e hub de configurações.

| Larguras revisadas estaticamente | Comportamento implementado                                                                                                                                           |
| -------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 320, 375, 390 e 430 px           | Menu em drawer, conteúdo sem largura reservada à sidebar, cards em vez de tabela, formulários em coluna única, controles acessíveis, overlays limitados ao viewport. |
| 768 px                           | Rail compacto, lista/chat lado a lado, detalhes em drawer, tabelas em cards.                                                                                         |
| 1024 px                          | Mesmo comportamento de notebook compacto, com conteúdo flexível e tabelas adaptadas.                                                                                 |
| 1280 e 1440 px                   | Tabelas completas, inbox com detalhes quando cabe, Kanban horizontal.                                                                                                |
| 1920 px                          | Conteúdo administrativo com largura máxima controlada; Inbox e quadro aproveitam a área operacional.                                                                 |

No mobile, a Inbox segue lista → chat → detalhes. O composer mantém área de envio e safe area. O viewport suporta `interactive-widget=resizes-content` onde o navegador oferecer o recurso. No Kanban existe seleção de coluna e alternativa por seletor de status no drawer, sem depender de drag-and-drop.

Esses comportamentos foram revisados no código/CSS; **não foram medidos em navegador nas nove larguras**.

## 6. Configurações

O novo catálogo agrupa somente páginas implementadas:

- Organização: Setores; Usuários e equipe (ADMIN).
- Atendimento: SLA e prioridades (ADMIN).
- Produtividade: templates globais (ADMIN) e respostas rápidas pessoais.

O hub inclui busca, descrições curtas, ícones SVG padronizados, grid responsivo e estado sem resultados. ATTENDANT acessa o hub, mas recebe apenas a opção pessoal autorizada. Os endpoints administrativos continuam protegidos no backend. A engrenagem permanece no rodapé da navegação.

As páginas internas usam breadcrumb por categoria. Não foram adicionados links para organização, contatos, etiquetas, canais ou segurança pessoal sem telas reais correspondentes.

## 7. Usuários e Setores

Ambos utilizam o mesmo CSS de diretório, toolbar, container, tabela, hover, skeleton, paginação de 12 registros, menu de ações, empty state e comportamento em cards. A paginação é local para as listas administrativas já fornecidas pela API; conversas e mensagens mantêm paginação no servidor.

Setores inclui nome/cor, descrição, total real de integrantes, status, data conhecida e ações. A data legada desconhecida aparece como “Não registrado”.

- Criação e edição no modal compartilhado.
- Status ACTIVE/INACTIVE, preservando vínculos.
- Visualização de integrantes por endpoint ADMIN, sem CPF/e-mail/senha no DTO.
- Inativação bloqueada quando existem atendimentos ativos com `departmentId`.
- Exclusão lógica bloqueada por integrantes vinculados ou histórico de conversas conhecido.
- Nenhuma exclusão física.
- Controle de versão para as edições que informam `expectedVersion`; erros de concorrência retornam 409.

Históricos legados sem `departmentId` não podem ser reconstruídos por inferência. A exclusão permanece lógica, preservando o registro relacional.

## 8. Componentes reutilizados/consolidados

- Diretório visual compartilhado (`directory.css`).
- `directoryPagination`.
- `tableSkeleton`.
- `actionMenu`, com fechamento externo/ESC, foco e posicionamento para cima quando necessário.
- `errorState`, com ação real de tentar novamente.
- `focusRegion`, usado por modais, navegação móvel e drawers.
- `appModal` existente simplificado, preservando seu contrato.
- `settingsBreadcrumb` e catálogo único de configurações.
- `departmentTag` e seletor existentes preservados.

Não foi criado um motor genérico de tabelas ou formulários. Os templates continuam separados dos controllers.

## 9. Inventário PostgreSQL

| Tabela             | Dependências/finalidade                      | Comportamento do seed                                                            |
| ------------------ | -------------------------------------------- | -------------------------------------------------------------------------------- |
| users              | Identidade, unicidade de username/e-mail/CPF | 8 novas identidades fictícias quando ausentes.                                   |
| members            | User HUMAN ou BOT                            | Criados pelo UserService; 8 HUMAN para os usuários novos.                        |
| departments        | Setores                                      | 6 setores identificados com `[Demo]`.                                            |
| member_departments | Relação N:N                                  | 14 vínculos esperados para os 7 atendentes, incluindo o inativo.                 |
| whatsapp_channels  | Provider/canal                               | 3 canais exclusivamente MOCK.                                                    |
| quick_replies      | Escopo e proprietário                        | Até 7 globais e 3 pessoais; globais existentes são preservados/referenciados.    |
| kanban_views       | Administrador proprietário                   | 3 views pessoais para o ADMIN demo.                                              |
| sla_policy         | Configuração única                           | Preservada integralmente; não é habilitada ou alterada pelo seed.                |
| work_tasks         | Usuário e conversa                           | 32 atividades.                                                                   |
| user_notifications | Usuário e recurso                            | 32 notificações vinculadas às tarefas.                                           |
| demo_seed_manifest | Namespace → IDs reais                        | Controle de criação/referência; 94 entradas previstas em uma base sem conflitos. |

`flyway_schema_history` é controle técnico de migrations e não recebe fixtures. **Contagens reais: não aferidas nesta sessão.** O relatório emitido pelo comando traz `postgresBefore` e `postgres` com totais consultados no banco.

## 10. Inventário MongoDB

| Coleção             | Volume previsto novo | Conteúdo                                                                                 |
| ------------------- | -------------------- | ---------------------------------------------------------------------------------------- |
| conversations       | 80                   | Conversas canônicas, com canais/usuários/setores reais do seed.                          |
| messages            | 480                  | TEXT, IDs Mock únicos, `simulated=true`, estados coerentes.                              |
| message_logs        | 480                  | Um evento por mensagem, inclusive falha simulada quando aplicável.                       |
| conversation_events | 187                  | Criação, atribuição, resoluções, encerramentos, transferências históricas e reaberturas. |
| conversation_reads  | 16                   | Fronteiras de leitura vinculadas a mensagens e Members reais.                            |
| conversation_drafts | 2                    | Modos EXTERNAL e INTERNAL em conversas autorizadas.                                      |
| team_entries        | 67                   | 40 notas e 27 mensagens internas.                                                        |

**Não há contagens reais de MongoDB nesta sessão.** `mongoBefore` e `mongo` são calculados pelo comando quando ele consegue executar.

## 11. Dados de demonstração

- 1 ADMIN adicional, 6 ATTENDANTS ativos e 1 ATTENDANT inativo.
- Usernames: `wxn.demo.admin`, `wxn.demo.attendant1` a `wxn.demo.attendant6`, `wxn.demo.inactive`.
- E-mails exclusivamente `example.com`; telefone de usuário nulo.
- CPFs gerados por algoritmo, identificados como sintéticos, validados e comparados com a unicidade existente. Não se afirma que números matematicamente gerados sejam reservados no cadastro civil; qualquer colisão bloqueia a criação.
- Nomes fictícios marcados como Demo.
- 40 identidades externas em conversas, reutilizadas entre canais; **não são registros de um módulo Contact inexistente**.
- Telefones na faixa fictícia +1 202 555 0100–0199, exclusivamente em canais Mock.
- Setores: Atendimento Geral, Comercial, Suporte Técnico, Financeiro, Pós-venda e Implantação, com sufixo `[Demo]`.
- 3 canais: Principal, Comercial e Suporte, marcados Mock/Demo.
- Conversas: 15 PENDING, 25 IN_PROGRESS, 12 WAITING_CUSTOMER, 20 RESOLVED, 8 CLOSED.
- Um atendimento ativo do ADMIN demo e variedade de responsáveis, prioridades e esperas.
- Dois históricos de reabertura, com ciclos anteriores conservados.
- Histórico de transferência direta entre HUMANs; sem fabricar workflow pendente inexistente.
- Templates globais `boas_vindas`, `despedida`, `horario`, `aguarde`, `transferencia`, `orcamento`, `suporte`; pessoais `minha_apresentacao`, `retorno`, `proposta` em contas distintas.
- Atividades: 21 abertas, 7 concluídas e 4 canceladas; 5 abertas vencidas no instante de criação.
- Notificações: atribuição/vencimento de atividades, 11 lidas e 21 não lidas, com datas diferentes.

Volumes acima descrevem a configuração preparada, não inserções já executadas. Registros existentes, remoções lógicas ou conflitos podem reduzir o número de criações.

## 12. Segurança e idempotência do seed

Comando: `npm run seed:demo -- --verify-idempotency`.

- Execução manual, nunca em startup ou migration.
- Perfil `dev` sem `prod`, HTTP somente em loopback, Origin ausente no servidor, sessão ADMIN, chave dev e CSRF.
- Senha inicial para contas novas vem de `WXN_DEMO_PASSWORD`; não há valor padrão/hardcoded ou impressão de senha.
- Usuários são criados pelo UserService, incluindo validações, BCrypt, unicidade e Member HUMAN.
- Manifesto PostgreSQL com IDs gerados pelo banco. Criação e manifesto de cada recurso relacional usam a mesma transação.
- Documentos MongoDB têm `seedKey` único e parcial no namespace `wxn-demo-v1`. Nenhum documento existente recebe uma marca de propriedade retroativa.
- Criação se ausente; não sobrescreve edições, senhas, soft deletes ou configurações existentes.
- Templates globais já existentes são referenciados sem alterar conteúdo.
- Fronteiras de leitura/rascunhos preexistentes são preservados pela chave natural.
- A referência ausente à última mensagem pode ser completada apenas em conversa do seed com versão zero e sem referência anterior, permitindo recuperação de uma execução interrompida.
- Falha parcial preserva o progresso; o relatório informa recursos criados/existentes e falhas. Não há transação distribuída entre PostgreSQL e MongoDB.
- Não há chamada a WhatsAppProvider, Z-API ou Twilio; são importações de histórico fictício nos modelos canônicos, com provider MOCK validado.
- A política SLA atual é respeitada. Com SLA desabilitado, a interface continua mostrando “Sem SLA configurado”; o seed não inventa risco por booleano.

A segunda execução **não foi confirmada contra bancos**. A opção `--verify-idempotency` executa o endpoint novamente e falha se houver novas criações ou falhas na segunda resposta.

## 13. Auditoria arquitetural e refatorações

A inspeção incluiu inventário global das fontes, rotas, contratos, entidades, repositories, migrations, estilos e buscas por listeners, timers, HTTP, coleções e consultas. As correções concentraram-se nos problemas observados, sem reescrever a stack.

- Extraída a navegação do arquivo de gerenciamento e separado o catálogo/hub de configurações.
- HTTP de tarefas, templates, SLA e relatório movido para services próprios.
- Estilo de diretório compartilhado em vez de um segundo padrão para setores.
- Consulta agregada para totais por setor.
- Leitura em lote de Users/Members com EntityGraph de setores, eliminando a consulta por registro nos fluxos de listagem revisados. Medição com SQL real ainda pendente.
- Dados assíncronos de notificações protegidos por geração/proprietário e consulta simultânea deduplicada.
- ResizeObserver da timeline removido no destroy; eventos de navegação/menus/foco também têm limpeza.
- Alterações limitadas a funcionalidade existente, demonstração e qualidade. Strategy WhatsApp foi preservada.

Não se afirma remoção global de todo código morto ou ausência absoluta de N+1 em todos os fluxos. AngularJS possui referências dinâmicas; somente a antiga folha de estilos foi removida após realocação e atualização do import.

## 14. Segurança corrigida e preservada

- ADMIN registra operações com seu próprio Member HUMAN, mesmo sem setor. V10 cria apenas vínculos ausentes; não altera usuários, senhas ou Members já existentes.
- Exclusão lógica de setor respeita vínculos e histórico conhecido.
- Conflitos otimistas agora têm resposta 409 em vez de erro genérico 500.
- Mensagens 400/401/403/404/409/422/429/500 têm apresentação consistente; stack traces não são enviados à interface.
- DTO de integrantes não expõe identificação pessoal.
- Foram preservadas autorização REST/STOMP, isolamento de atendentes, CSRF e validações de CPF/e-mail/username.
- Nenhuma permissão foi relaxada para habilitar uma tela.

## 15. Lint e formatação

ESLint, Prettier, EditorConfig e Checkstyle já existiam; foram mantidos. Não foram instaladas ferramentas concorrentes. ESLint passou a rejeitar `console.log` acidental no frontend, mantendo warn/error e a saída necessária de scripts CLI.

Migrations continuam excluídas da formatação. Arquivos gerados e dependências também. `lint:fix`, `lint`, `format:check`, build Maven e build Vite foram executados. Nenhuma regra foi desabilitada para mascarar erros.

## 16. Verificações executadas

- Build backend com `mvn -DskipTests package`.
- Build frontend com `npm run build`.
- `npm run lint`, `npm run format:check` e `git diff --check`.
- 7 testes frontend existentes.
- Suíte Java existente com agente Mockito explícito: 39 testes declarados, 24 executados, 15 integrações ignoradas por dependerem de ambiente; sem nova suíte.
- Checagem temporária em memória das fixtures: 80 conversas, 480 mensagens, distribuição por estado, IDs externos únicos, ordenação temporal, ausência de datas futuras, ciclos finalizados e 8 CPFs válidos/distintos. Nenhum banco foi acessado nessa checagem.
- Análise estática de assinaturas de arrays DI AngularJS; nenhuma divergência encontrada na execução realizada.
- Recusa do CLI para perfil prod e destino HTTPS externo, antes de qualquer conexão.
- Tentativa do comando completo de seed: interrompido pela falta de variáveis de credenciais; nenhuma criação declarada.
- Docker: acesso ao socket recusado pelo sandbox.
- Chrome headless: abortou nas duas tentativas; sem validação visual.

Os builds identificaram imports Java faltantes durante a refatoração; foram corrigidos antes da verificação final. Não há alegação de validação REST/STOMP/login real após a migration V10 neste ambiente.

## 17. Migration

`V10__department_presentation_and_demo_manifest.sql`:

- description/status/datas/version em departments.
- Status ACTIVE para compatibilidade; datas antigas ficam nulas.
- Tabela de manifesto do seed.
- Members HUMAN apenas para administradores não excluídos que ainda não tenham um vínculo HUMAN não excluído.

V1–V9 não foram alteradas. V10 ainda precisa ser aplicada pelo Flyway no ambiente local com bancos disponíveis.

## 18. Como executar

Na raiz, inicie bancos e backend com o `.env` atual:

```bash
set -a
source .env
set +a
docker compose up -d
mvn spring-boot:run
```

Em outro terminal:

```bash
npm --prefix frontend run dev -- --host localhost --port 4002
```

Para o seed, com uma conta ADMIN existente, use variáveis locais. Exemplo para zsh, sem senha no histórico:

```zsh
read "WXN_SEED_USERNAME?Usuário ADMIN: "
read -s "WXN_SEED_PASSWORD?Senha ADMIN: "
printf '\n'
read -s "WXN_DEMO_PASSWORD?Senha para as novas contas demo: "
printf '\n'
export WXN_SEED_USERNAME WXN_SEED_PASSWORD WXN_DEMO_PASSWORD
npm run seed:demo -- --verify-idempotency
unset WXN_SEED_USERNAME WXN_SEED_PASSWORD WXN_DEMO_PASSWORD
```

O script lê a configuração `.env` (ou `WXN_ENV_FILE`) e exige `DEV_API_KEY` e perfil dev. O resultado contém contagens reais antes/depois e por recurso. Em caso de falha parcial, corrija a causa e execute novamente; não apague os bancos.

Após sucesso, atualize a interface e entre como `wxn.demo.admin` ou `wxn.demo.attendant1` usando a senha demo fornecida. Views salvas são pessoais ao ADMIN demo; o ADMIN original continua tendo acesso às conversas globais. As contas anteriores mantêm as senhas existentes.

Para demonstrar SLA, habilite-o manualmente apenas se isso for adequado ao seu ambiente; o seed não altera essa decisão. Os timestamps preparados representam espera curta, aviso e atraso para a política padrão, mas a classificação real depende da política atual e do relógio.

## 19. Limitações e pendências

1. Falta execução integrada com V10 aplicada, bancos acessíveis, dois perfis, REST/STOMP e duas passagens do seed.
2. Falta inspeção visual real nas nove larguras e com teclado virtual.
3. Contatos, etiquetas, campos personalizados, menções, workflow de transferência e human handoff continuam ausentes; não foram inventados para preencher o seed.
4. Configuração de organização, presença, distribuição automática e perfil pessoal completo continuam fora do produto atual.
5. O SLA existente usa minutos corridos e pode estar desabilitado; não há calendário de horários úteis novo.
6. As métricas e relatórios mantêm os limites funcionais anteriores; o seed não cria séries artificiais no frontend.
7. Paginação administrativa é local; não foi introduzido um novo contrato de paginação de equipe/setores no backend.
8. O legado conserva estilos específicos da Inbox; os tokens e overrides consolidam a base, mas não se afirma eliminação de todo valor literal de CSS.
9. Contagens de banco no relatório dependem de execução real. Os valores previstos não substituem essa medição.

## 20. Arquivos desta tarefa

Comparação por hash com o início desta tarefa; alterações anteriores do workspace foram preservadas. AGENTS.md permaneceu intacto.

### Criados (26)

- `docs/product-polish-report.md`
- `frontend/src/features/departments/department-directory.directive.js`
- `frontend/src/features/departments/department-directory.html`
- `frontend/src/features/quick-replies/quick-replies.service.js`
- `frontend/src/features/reports/reports.service.js`
- `frontend/src/features/settings/navigation.directive.js`
- `frontend/src/features/settings/settings-breadcrumb.html`
- `frontend/src/features/settings/settings-catalog.js`
- `frontend/src/features/settings/settings-hub.directive.js`
- `frontend/src/features/sla/sla.service.js`
- `frontend/src/features/tasks/tasks.service.js`
- `frontend/src/shared/components/action-menu.html`
- `frontend/src/shared/components/directory-pagination.html`
- `frontend/src/shared/components/directory.directive.js`
- `frontend/src/shared/components/error-state.directive.js`
- `frontend/src/shared/components/error-state.html`
- `frontend/src/shared/components/focus-region.directive.js`
- `frontend/src/shared/components/table-skeleton.html`
- `frontend/src/shared/styles/directory.css`
- `frontend/src/shared/styles/product.css`
- `scripts/seed-demo.mjs`
- `src/main/java/com/wxn/chatbot/demo/controller/LocalDemoController.java`
- `src/main/java/com/wxn/chatbot/demo/repository/DemoSeedStore.java`
- `src/main/java/com/wxn/chatbot/demo/service/DemoFixtures.java`
- `src/main/java/com/wxn/chatbot/demo/service/LocalDemoService.java`
- `src/main/resources/db/migration/V10__department_presentation_and_demo_manifest.sql`

### Modificados (58)

- `README.md`
- `docs/departments.md`
- `docs/user-accounts.md`
- `eslint.config.mjs`
- `frontend/src/app/app.routes.js`
- `frontend/src/app/main.js`
- `frontend/src/core/http/http-interceptor.js`
- `frontend/src/features/auth/login.html`
- `frontend/src/features/chats/chats.controller.js`
- `frontend/src/features/chats/operational-event-label.js`
- `frontend/src/features/chats/templates/chat-layout.html`
- `frontend/src/features/collaboration/team-panel.css`
- `frontend/src/features/collaboration/team-panel.directive.js`
- `frontend/src/features/collaboration/team-panel.html`
- `frontend/src/features/dashboard/dashboard.html`
- `frontend/src/features/kanban/kanban.css`
- `frontend/src/features/kanban/kanban.directive.js`
- `frontend/src/features/kanban/kanban.html`
- `frontend/src/features/messages/directives/message-list.directive.js`
- `frontend/src/features/notifications/notifications.html`
- `frontend/src/features/notifications/notifications.js`
- `frontend/src/features/quick-replies/quick-replies.directive.js`
- `frontend/src/features/quick-replies/quick-replies.html`
- `frontend/src/features/reports/reports.directive.js`
- `frontend/src/features/reports/reports.html`
- `frontend/src/features/settings/department-tag.html`
- `frontend/src/features/settings/management.directive.js`
- `frontend/src/features/settings/management.html`
- `frontend/src/features/settings/management.service.js`
- `frontend/src/features/settings/navigation.html`
- `frontend/src/features/settings/settings-hub.html`
- `frontend/src/features/sla/sla.directive.js`
- `frontend/src/features/sla/sla.html`
- `frontend/src/features/tasks/tasks.directive.js`
- `frontend/src/features/tasks/tasks.html`
- `frontend/src/features/users/directives/user-directory.directive.js`
- `frontend/src/features/users/templates/user-directory.html`
- `frontend/src/index.html`
- `frontend/src/shared/directives/app-modal.directive.js`
- `frontend/src/shared/templates/app-modal.html`
- `package.json`
- `src/main/java/com/wxn/chatbot/common/ApiErrorHandler.java`
- `src/main/java/com/wxn/chatbot/config/MongoConfiguration.java`
- `src/main/java/com/wxn/chatbot/conversation/document/Conversation.java`
- `src/main/java/com/wxn/chatbot/conversation/repository/ConversationRepository.java`
- `src/main/java/com/wxn/chatbot/department/controller/DepartmentController.java`
- `src/main/java/com/wxn/chatbot/department/dto/DepartmentDtos.java`
- `src/main/java/com/wxn/chatbot/department/entity/Department.java`
- `src/main/java/com/wxn/chatbot/department/mapper/DepartmentMapper.java`
- `src/main/java/com/wxn/chatbot/department/service/DepartmentService.java`
- `src/main/java/com/wxn/chatbot/member/repository/MemberRepository.java`
- `src/main/java/com/wxn/chatbot/member/service/MemberService.java`
- `src/main/java/com/wxn/chatbot/notification/entity/UserNotification.java`
- `src/main/java/com/wxn/chatbot/quickreply/controller/QuickReplyController.java`
- `src/main/java/com/wxn/chatbot/quickreply/repository/QuickReplyRepository.java`
- `src/main/java/com/wxn/chatbot/quickreply/service/QuickReplyService.java`
- `src/main/java/com/wxn/chatbot/user/repository/UserRepository.java`
- `src/main/java/com/wxn/chatbot/user/service/UserService.java`

### Removidos (1)

- `frontend/src/features/users/users.css`
