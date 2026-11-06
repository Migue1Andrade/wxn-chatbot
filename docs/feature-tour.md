# Guia rápido de contexto e demonstração

Este documento é um mapa funcional do WXN Chatbot. A aplicação é um painel de atendimento
WhatsApp: o atendente trabalha na Inbox, enquanto o ADMIN acompanha a operação e configura os
recursos auxiliares.

## Preparar uma demonstração completa

1. Suba os bancos e o backend conforme o [README](../README.md).
2. Crie duas contas locais (uma ADMIN e uma ATTENDANT) com `node scripts/seed-local-users.mjs`.
3. Defina `WXN_SEED_USERNAME`, `WXN_SEED_PASSWORD` e `WXN_DEMO_PASSWORD` no ambiente.
4. Execute `npm run seed:demo -- --verify-idempotency`.
5. Inicie o frontend e abra `http://localhost:4002`.

O seed usa somente dados fictícios e canais `MOCK`. Ele é idempotente: a segunda execução deve
reutilizar os registros identificados pelo manifesto, sem criar duplicatas. O relatório do comando
mostra os recursos criados/existentes e as contagens antes/depois.

## Telas e o que testar

| Tela                     | Acesso                                   | O que ela concentra                                                                                               |
| ------------------------ | ---------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| Login                    | `/login`                                 | Sessão, logout, proteção por perfil e CSRF.                                                                       |
| Inbox                    | `/`                                      | Conversas, busca, filtros, atribuição, transferência, estados, leitura e tempo real.                              |
| Conversa                 | Dentro da Inbox                          | Histórico de mensagens TEXT, envio Mock, respostas rápidas `@@`, rascunho, notas, discussão interna e atividades. |
| Kanban                   | `/kanban` (ADMIN)                        | Colunas por status, prioridade, atenção/SLA, filtros, paginação, drawer de detalhes e movimentação.               |
| Dashboard                | `/dashboard` (ADMIN)                     | Totais reais por status e atualização após eventos operacionais.                                                  |
| Atividades               | `/tasks`                                 | Tarefas, follow-ups e lembretes; criação, edição, conclusão, cancelamento e vencimento.                           |
| Notificações             | `/notifications`                         | Avisos individuais, contador do sino e marcação de leitura.                                                       |
| Relatórios               | `/reports` (ADMIN)                       | Exportação CSV dos atendimentos atuais.                                                                           |
| Configurações            | `/settings`                              | Catálogo de configurações disponíveis para o perfil atual.                                                        |
| Usuários e equipe        | `/members` (ADMIN)                       | Usuários, perfis, status, CPF/e-mail/username, senha inicial e setores.                                           |
| Setores                  | `/settings/departments` (ADMIN)          | Criação, edição, cor, status e consulta de integrantes.                                                           |
| Canais WhatsApp          | `/settings/channels` (ADMIN)             | Canais, diagnóstico, capacidades, simulação Mock, callbacks e operações administrativas.                          |
| SLA e prioridades        | `/settings/sla` (ADMIN)                  | Política única de SLA, limites, aviso e timezone.                                                                 |
| Minhas respostas rápidas | `/settings/quick-replies`                | Templates pessoais usados pelo atalho `@@` na conversa.                                                           |
| Templates globais        | `/settings/global-quick-replies` (ADMIN) | Templates compartilhados com a equipe.                                                                            |

## Roteiro recomendado

1. No Login, entre como ADMIN e confirme o acesso ao Dashboard, Kanban e Configurações.
2. No Dashboard, confira a distribuição inicial. Abra o Kanban e filtre uma prioridade ou um
   responsável; abra um card para ver histórico, SLA e ações.
3. Na Inbox, abra uma conversa PENDING, assuma-a e mude o estado. Envie uma mensagem pelo Mock e
   observe a mensagem, o status e o evento operacional.
4. Na mesma conversa, crie uma nota, uma mensagem interna, um rascunho e uma atividade. Depois
   consulte Atividades e Notificações para conferir os efeitos persistidos.
5. Em Respostas rápidas, crie um template pessoal e use `@@atalho` no compositor. Variáveis não
   resolvidas são mostradas e impedem o envio até serem preenchidas.
6. Em Canais, abra um canal Mock, rode o diagnóstico e use o simulador de entrada. Volte à Inbox
   para confirmar que o evento chegou sem chamar um número real.
7. Em Usuários e Setores, crie/edite um registro e confirme as validações de unicidade e vínculo.
8. Em SLA, habilite a política apenas se quiser visualizar estados `ON_TRACK`, `AT_RISK` e
   `BREACHED` no Kanban; o seed preserva a configuração existente.
9. Exporte o CSV em Relatórios e teste logout/login com uma conta ATTENDANT. Recursos ADMIN devem
   desaparecer e as conversas ficam limitadas ao escopo autorizado do atendente.

## Limites atuais

O produto está populado apenas com recursos que existem no domínio. Ainda não há telas para
contatos independentes, etiquetas, campos personalizados, presença/distribuição automática,
menções, solicitações de transferência/handoff, preferências pessoais completas, calendário de
horário útil do SLA ou edição de mensagens no provedor. Z-API e Twilio dependem de configuração
externa; para uma demonstração segura e repetível, use o Mock.

## Verificações técnicas

Na raiz do projeto:

```bash
npm run lint
npm --prefix frontend test
npm --prefix frontend run build
mvn test
git diff --check
```

Para validar a fila com bancos isolados, use `bash scripts/verify-queue.sh`. O seed completo exige
backend local, perfil `dev`, uma sessão ADMIN e as variáveis descritas acima; ele não deve ser
executado em produção.
