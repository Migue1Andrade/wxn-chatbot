# Usuários e contas locais

O cadastro único em Profissionais gerencia ADMIN e ATTENDANT com nome, username, e-mail, CPF, telefone,
perfil, setores múltiplos e status. Senha inicial e confirmação são obrigatórias no cadastro. A edição
não altera senhas existentes. Registros anteriores incompletos exibem os campos de senha inicial para
concluir o cadastro, preservando os IDs de User e Member.

A migration **V4__add_user_accounts.sql** acrescenta os campos à tabela `users`. V1–V3 permanecem
inalteradas. Não é necessário resetar bancos nem executar Flyway repair. Nome, telefone, IDs e vínculos
anteriores são preservados. As contas anteriores recebem ATTENDANT/INACTIVE e identidade nula, sem
inventar CPF, e-mail, username ou senha. Sua conclusão deve ser feita por um ADMIN.

A constraint de identidade usa `NOT VALID` para preservar registros antigos; novos registros e alterações
exigem a identidade completa, mesmo em contas inativas. Esse comportamento é definido pelo
[PostgreSQL](https://www.postgresql.org/docs/17/sql-altertable.html).

CPF é String de 11 dígitos, com validação dos dois verificadores e rejeição de repetições no backend.
O formulário aplica máscara e validação equivalentes. Username é aparado e comparado sem distinguir
caixa; e-mail é aparado e armazenado em minúsculas. Índices únicos globais incluem registros inativos
e excluídos logicamente. CPF não é utilizado como login ou senha. A API fornece mensagens específicas
para duplicidades, incluindo as detectadas por constraints em condições concorrentes.

Senhas usam `BCryptPasswordEncoder` com custo 12, compatível com
[Spring Security](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).
A dependência adicionada é apenas `spring-security-crypto`, responsável pelo hash. Não há senha, hash ou
confirmação em respostas da API. As senhas aceitam pelo menos 8 caracteres e no máximo 72 bytes UTF-8.

## Login e acesso

O login utiliza username e senha. A autenticação usa sessão nativa do servlet, cookie HttpOnly/SameSite=Lax,
renovação do ID no login e token anti-CSRF para operações de escrita. O timeout é de 30 minutos de inatividade.
A chave técnica de desenvolvimento, os perfis dev/prod e a validação da origem continuam sendo exigidos.
Não há JWT, recuperação de senha, convites ou deploy nesta entrega.

- ADMIN: gestão de usuários, setores, canais e funções de atendimento. Dashboard e Kanban administrativos utilizam os registros reais da operação.
- ATTENDANT: chat, consulta operacional de canais/membros e ações de atendimento com seu próprio Member.
  Rotas administrativas são bloqueadas também no backend; esconder ícones não é o mecanismo de autorização.
- O último ADMIN ativo não pode ser inativado nem rebaixado. A transação bloqueia os administradores antes
  de conferir a regra, evitando que duas alterações simultâneas removam todos os administradores ativos.
- ATENDENTES exigem ao menos um setor. ADMIN pode ficar sem setor, mas possui Member HUMAN próprio para autoria das operações.
  A migration V10 cria apenas vínculos ausentes em administradores legados. Inativação e edição
  sincronizam o Member existente sem recriar IDs.
- WebSocket exige sessão autenticada, além da chave técnica. Sessões encerradas ou contas indisponíveis
  não podem continuar recebendo eventos. SEND continua bloqueado; operações usam REST com CSRF.

Endpoints:

| Método     | Caminho               | Uso                                                                  |
| ---------- | --------------------- | -------------------------------------------------------------------- |
| GET        | `/api/auth/session`   | Identidade resumida e token CSRF; cria sessão anônima antes do login |
| POST       | `/api/auth/login`     | Username e senha; retorna identidade resumida e novo token CSRF      |
| POST       | `/api/auth/logout`    | Invalida a sessão                                                    |
| GET, POST  | `/api/users`          | Listagem e cadastro por ADMIN                                        |
| GET, PATCH | `/api/users/{id}`     | Consulta e edição por ADMIN                                          |
| POST       | `/api/dev/users/seed` | Bootstrap manual local, exclusivamente dev                           |

O frontend inclui cookies com `withCredentials` e o header `X-CSRF-Token`. Clientes manuais precisam
preservar o cookie recebido em `/api/auth/session`, enviar seu `csrfToken` no login e usar o novo token
retornado pelo login nas próximas escritas. O cookie não contém CPF, senha, perfil ou dados pessoais.
A identidade é consultada no backend a cada requisição; perfil ou status antigos não são confiados ao cliente.

O cadastro HUMAN antigo em `/api/members` foi substituído pelo cadastro completo em `/api/users`.
`POST /api/members` permanece somente para BOT; a edição de dados de HUMAN e de seus setores ocorre
exclusivamente por `/api/users/{id}`. Não há um segundo formulário para ADMIN.

## Seed manual

Com o backend atualizado e a migration V4 aplicada, execute na raiz:

```bash
node scripts/seed-local-users.mjs
```

O script contém somente as duas contas demonstrativas solicitadas: `Administrador` (ADMIN) e `Atendente`
(ATTENDANT). Ambos são ACTIVE, têm e-mails/CPFs distintos e válidos e telefone nulo. Apenas o atendente
precisa de HUMAN ativo e setor; se não houver setores, o seed cria Atendimento com a cor `#2563EB`.
As credenciais de demonstração ficam exclusivamente no script manual, não em migrations ou configurações.

O endereço é fixado em loopback, o ambiente deve conter dev e não prod, e a chave técnica é obrigatória.
O backend rejeita requests do seed com Origin de navegador ou endereço remoto não local. Não existe
execução automática no startup. Toda a operação ocorre em uma transação.

Identidades totalmente correspondentes retornam `EXISTING_UNCHANGED`: nome, perfil, status, senha e vínculos
existentes são preservados. Qualquer conflito entre username, e-mail ou CPF interrompe o lote sem alterar
contas. A proteção vale também para contas inativas ou excluídas. Uma conta preexistente inativa não é
reativada silenciosamente. O script informa somente username, resultado, ID e status efetivo; não imprime CPF ou senha.

Para os exemplos opcionais de conversas, `scripts/seed-chat-examples.mjs` exige credenciais de um ADMIN
nas variáveis de processo `WXN_SEED_USERNAME` e `WXN_SEED_PASSWORD`. Ele reutiliza os atendentes existentes,
sem criar usuários incompletos nem atribuir telefones fictícios às contas.

## Verificação desta entrega

- Compilação Java e build frontend executados.
- 24 testes unitários Java existentes e 7 testes frontend existentes passaram; nenhuma suíte nova criada.
- Auditoria isolada temporária verificou CPF, máscara, BCrypt, login das duas contas, unicidade de todos
  os campos, edição com os próprios dados, identidade inativa, último ADMIN, perfis, CSRF, execução do
  seed duas vezes sem alterar hashes e rejeição de conflito antes de escrita. Usa repositories em memória;
  não comprova a execução de SQL ou comportamento visual no navegador.
- O seed real foi tentado, mas a conexão ao backend local foi bloqueada por `EPERM` nesta sessão.
  Nenhuma conta foi declarada criada no banco real. Aplicação da migration V4, execução real do seed,
  login no navegador e verificação dos índices PostgreSQL permanecem dependentes do ambiente local.
