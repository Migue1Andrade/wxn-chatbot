# Setores e atendentes

O hub `#!/settings` apresenta o card Setores. A gestão fica em `#!/settings/departments`, com retorno ao hub.
O cadastro e a edição de atendentes ficam em `#!/members`.

Os endpoints abaixo usam o prefixo `/api` e a proteção de desenvolvimento já existente.
A WXN trabalha com uma organização única; a gestão exige sessão autenticada com perfil ADMIN.

- `GET /departments`: setores disponíveis, com `memberCount` de membros distintos, inclusive inativos.
- `POST /departments`: cria um setor com `name` e `color` hexadecimal (`#2563EB`).
- `PATCH /departments/{id}`: altera nome e cor.
- `DELETE /departments/{id}`: exclusão lógica, bloqueada por qualquer vínculo existente.
- `POST /users`: recebe o cadastro completo, incluindo username, e-mail, CPF, senha/confirmação, perfil,
  status e setores. ATTENDANT exige `departmentIds`; ADMIN pode ficar sem setor.
- `PATCH /users/{id}`: atualiza a identidade e o conjunto final de setores, preservando User e Member.
- `GET /users`: lista identidades e setores, acessível somente a ADMIN.
- `GET /members`: fornece membros operacionais e setores sem expor CPF, e-mail ou credenciais.

O login e os contratos completos estão em [contas](user-accounts.md).

Cadastro:

Use o formulário único de usuários; não é mais permitido cadastrar HUMAN incompleto via `/api/members`.

IDs repetidos, inexistentes ou excluídos rejeitam a operação inteira.
BOT não recebe setores. A busca da tela de atendentes considera o nome de qualquer setor associado,
sem multiplicar registros na listagem.

A migration V1 cria usuários e membros, V2 cria setores e a associação N:N, e V3 cria canais.
A associação possui chave primária composta e chaves estrangeiras para membros e setores, impedindo
duplicações e vínculos inválidos. A aplicação bloqueia os setores selecionados durante a transação
para coordenar associações com a exclusão. V4 acrescenta o cadastro de contas preservando os usuários
existentes e seus vínculos. Não há reset nesta entrega.

## Revisão de produto — V10

Usuários e Setores compartilham container, tabela, filtros, ordenação, paginação de 12 registros,
skeleton e menu de ações. O endpoint `GET /api/departments/{id}/members` lista integrantes sem
expor CPF, e-mail ou senha. Os totais vêm de consulta agregada no banco.

Setores agora incluem `description`, `status`, `createdAt`, `updatedAt` e `version`.
Novas edições enviam `expectedVersion`; conflitos retornam 409. Datas antigas ausentes
continuam nulas e aparecem como “Não registrado”. Não se fabrica uma data histórica.

Inativação preserva vínculos. Atendimentos ativos impedem inativação; vínculos de integrantes
ou histórico de conversas impedem exclusão lógica. Nenhuma exclusão física foi adicionada.

Consulte [o relatório de polimento](product-polish-report.md) para validações e limites.
