# Projeto WXN Chatbot — Regras do Projeto

## Arquitetura

- O projeto é multi-tenant/multiempresa.
- `companyId` é o identificador central do tenant.
- Dados pertencentes a uma empresa devem possuir `companyId`.
- O backend nunca deve confiar cegamente em `companyId` enviado pelo cliente.
- Posteriormente, o `companyId` deverá ser obtido a partir do contexto autenticado do usuário.
- Controllers não acessam repositories diretamente.
- Fluxo padrão: Controller -> Service -> Repository.
- Repositories são responsáveis por persistência.
- Services concentram regras de negócio.
- DTOs e entidades de persistência devem ser separados.
- Entidades JPA nunca devem ser retornadas diretamente pela API.
- Evitar abstrações genéricas sem necessidade concreta.
- Não criar infraestrutura ou tecnologias adicionais sem justificativa técnica.

## Banco relacional

- PostgreSQL é o banco principal do sistema.
- JPA/Hibernate será utilizado através do Spring Data JPA.
- Cada entidade persistente deve possuir seu próprio Repository quando aplicável.
- CRUD básico deve utilizar os recursos fornecidos pelo Spring Data.
- Não implementar CRUD manualmente sem necessidade.
- Consultas dinâmicas devem utilizar os mecanismos apropriados do Spring Data/JPA, como Specifications, quando necessário.
- IDs do PostgreSQL serão gerados pelo próprio banco.
- O schema será controlado por Flyway.
- Não utilizar `ddl-auto=create` ou `ddl-auto=update` como mecanismo de evolução do banco.
- Alterações de schema devem ser feitas através de novas migrations.
- Banco utiliza `snake_case`.
- Código Java utiliza `camelCase`.

## MongoDB

- MongoDB será utilizado para dados conversacionais e estruturas com maior variabilidade.
- Inicialmente teremos como conceito as coleções `conversations`, `messages` e `message_logs`.
- Documentos relacionados a uma empresa devem possuir `companyId`.
- O `_id` nativo do MongoDB deve ser utilizado inicialmente.
- Não criar mecanismo próprio de geração de IDs sem necessidade.

## Normalização e mapeamento

- A representação do banco não deve vazar diretamente para a API.
- Dados persistidos em `snake_case` devem ser expostos pela aplicação em `camelCase` quando aplicável.
- A conversão entre persistência, domínio e resposta deve ficar centralizada em uma camada apropriada de Mapper/Factory.
- Não espalhar conversões manuais pelos Controllers.
- A solução deve ser simples e fácil de alterar posteriormente.
- Não adicionar bibliotecas de mapeamento apenas por conveniência sem avaliar primeiro se são realmente necessárias.

## Segurança

- Senhas nunca podem ser armazenadas em texto puro.
- Credenciais, tokens, API keys e secrets nunca devem ser commitados.
- Variáveis sensíveis devem utilizar configuração externa/environment variables.
- Não implementar autenticação completa nesta etapa.
- Não inventar regras de autorização que ainda não foram definidas.

## Qualidade

- Código deve priorizar simplicidade, legibilidade e manutenção.
- Evitar overengineering.
- Não duplicar lógica.
- Não criar código especulativo para funcionalidades futuras.
- Preferir recursos nativos e maduros do Spring Boot/Spring Data.
- Alterações devem ser pequenas e coerentes.
- Não modificar arquivos não relacionados à tarefa.
- Não adicionar dependências sem justificativa.
- Quando existir dúvida arquitetural relevante, parar e perguntar em vez de tomar uma decisão arbitrária.

## Git

- Não criar commits automaticamente.
- Não fazer push automaticamente.
- Nunca adicionar secrets ao repositório.
- Manter alterações organizadas e fáceis de revisar.
- Antes de considerar uma tarefa concluída, verificar o diff.

## Formatação e lint obrigatórios

- Ler `docs/code-style.md` antes de criar ou alterar código.
- Usar `.editorconfig`, `.prettierrc.json`, `eslint.config.mjs` e `config/checkstyle.xml` como fontes das regras automatizadas.
- Java e XML: 4 espaços. JavaScript, HTML, CSS, JSON, YAML e shell: 2 espaços. Não usar tabs.
- Usar quebras de linha LF, newline no fim do arquivo e largura alvo de 110 caracteres.
- Não comprimir classes, métodos, condicionais ou declarações em uma linha. Deixar o formatador organizar os blocos.
- Java e JavaScript: usar chaves em condicionais e laços; não juntar várias instruções na mesma linha.
- JavaScript: usar `const` por padrão, `let` quando necessário, nunca `var`.
- HTML: um atributo por linha em elementos com múltiplos atributos; templates separados dos controllers.
- Priorizar nomes claros, funções com responsabilidade única e tratamento explícito de erros. Formatação não substitui revisão das regras de negócio.
- Não desativar lint ou usar `prettier-ignore` para contornar uma regra sem justificativa concreta.
- Nunca reformatar migrations já existentes/aplicadas: isso altera checksums do Flyway. Alterações de schema exigem nova migration.
- Não adicionar bibliotecas de runtime para resolver apresentação ou formatação.
- Antes de concluir: executar `npm run lint:fix`, `npm run lint`, as verificações relevantes da alteração e `git diff --check`; revisar o diff, inclusive arquivos novos.
- Nunca executar commit ou push sem pedido explícito do usuário. O workflow de lint apenas verifica e não altera arquivos.
