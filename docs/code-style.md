# Padrões de código

Este documento define o padrão do projeto para pessoas e agentes. As regras arquiteturais de `AGENTS.md` continuam obrigatórias. Antes de alterar código, consulte estas regras e as configurações versionadas.

## Ferramentas e comandos

As ferramentas de desenvolvimento ficam no `package.json` da raiz e suas versões exatas no lockfile. O lint Java exige JDK 21 e Maven. O frontend mantém suas dependências em `frontend/`. Use Node 22.23.2 ou mais recente; `.nvmrc` registra a versão de referência.

```bash
nvm use
npm ci
npm --prefix frontend ci
```

| Comando na raiz        | Efeito                                                                         |
| ---------------------- | ------------------------------------------------------------------------------ |
| `npm run format`       | Formata os arquivos suportados pelo Prettier                                   |
| `npm run format:check` | Verifica a formatação sem gravar arquivos                                      |
| `npm run lint:js`      | Verifica JavaScript da aplicação, testes, scripts e configurações              |
| `npm run lint:java`    | Verifica chaves, instruções por linha e whitespace Java com Checkstyle         |
| `npm run lint:shell`   | Verifica a sintaxe dos scripts Bash                                            |
| `npm run lint:fix`     | Aplica correções seguras do ESLint e executa o formatador                      |
| `npm run lint`         | Verifica formatação, JavaScript, Java e sintaxe Bash; falha em erros ou avisos |
| `npm run check`        | Executa lint, testes do frontend e build Vite                                  |

O formatador não corrige todas as violações: por exemplo, chaves ausentes em Java apontadas pelo Checkstyle devem ser adicionadas ao código antes de repetir a verificação.

Para Java, execute também as verificações Maven adequadas à mudança. Para a integração com os bancos, use `bash scripts/verify-queue.sh`, que executa o lint antes de subir os bancos de teste.

## Indentação e quebras de linha

- Use espaços, nunca tabs.
- Java e XML: 4 espaços por nível. SQL novo segue 4 espaços.
- JavaScript, HTML, CSS, JSON, YAML e shell: 2 espaços por nível.
- Codificação UTF-8, quebra de linha LF e uma quebra ao final do arquivo.
- Largura alvo de 110 caracteres. Não quebre artificialmente URLs, strings ou classes HTML para satisfazer um limite rígido.
- Não escreva classes, funções, blocos CSS ou vários comandos em uma única linha. O formatador organiza o código suportado de forma consistente.
- Não alinhe usando séries de espaços adicionais. Deixe o formatador decidir os recuos e as quebras.

`.editorconfig` orienta editores compatíveis. `.prettierrc.json` é a fonte da formatação automática. O Prettier com os plugins Java e XML cuida também do backend e do `pom.xml`. SQL e shell recebem indentação pelo EditorConfig; SQL não é reescrito automaticamente e Bash tem verificação de sintaxe.

## JavaScript e AngularJS

- Use `const` por padrão e `let` quando houver reatribuição. Não use `var`.
- Use aspas simples, ponto e vírgula e vírgulas finais conforme o Prettier.
- Condicionais e laços devem ter chaves, inclusive quando possuem uma única instrução.
- Use igualdade estrita; `value == null` é permitido para verificar conjuntamente `null` e `undefined`.
- Não mantenha variáveis/imports sem uso, imports duplicados ou referências a nomes não definidos.
- Prefixe com `_` apenas parâmetros que precisam existir pela assinatura da API e não são utilizados.
- Mantenha HTTP nos services, coordenação nos controllers e apresentação nos templates/diretivas, como na arquitetura existente.

```javascript
function canReply(conversation, member) {
  if (!member || conversation.status === 'CLOSED') {
    return false;
  }

  return Boolean(conversation.channelId);
}
```

O ESLint aplica as regras recomendadas e as regras locais em `eslint.config.mjs`. O `eslint-config-prettier` evita disputas com o formatador; regras de indentação não são duplicadas no ESLint.

## Java e persistência

- Use `camelCase` para campos/métodos, `PascalCase` para tipos e `UPPER_SNAKE_CASE` para constantes.
- Use blocos explícitos com chaves e métodos que representem uma responsabilidade. Não junte vários testes ou declarações na mesma linha.
- Mantenha Controller → Service → Repository. Não mova persistência para controllers por conveniência.
- Mantenha DTOs separados das entidades, canais identificados e conversões centralizadas nos mappers.
- Evite abstrações, dependências e métodos genéricos sem necessidade concreta.
- O Prettier verifica a apresentação Java. O Checkstyle (`config/checkstyle.xml`) exige chaves nos condicionais/laços, uma instrução por linha e arquivos sem tabs. A checagem inclui código de produção e testes, e executa na fase `validate` do Maven. Compilação, testes e revisão continuam necessários para validar comportamento e arquitetura.

## HTML, CSS e configuração

- Mantenha templates HTML em arquivos separados dos controllers.
- Use um atributo por linha em elementos com múltiplos atributos; deixe o Prettier organizar abertura e fechamento das tags.
- Organize CSS em blocos com declarações em linhas separadas, seletores claros e propriedades relacionadas próximas.
- Reutilize estilos e utilitários já existentes. Não adicione bibliotecas apenas para formatar ou apresentar um componente simples.
- Use nomes e mensagens claros, ações acessíveis por teclado e labels nos controles.
- Mantenha segredos em variáveis de ambiente. `.env` e artefatos gerados ficam fora da formatação e do versionamento.

## Migrations imutáveis

`src/main/resources/db/migration/` está excluído da formatação automática. Mesmo uma mudança de espaços em uma migration aplicada altera o checksum verificado pelo Flyway.

Para uma nova migration, escreva SQL legível com indentação de 4 espaços, uma instrução por bloco e linhas separadas para colunas/restrições. Revise antes de aplicar. Após aplicada, faça mudanças somente em uma nova migration.

## Rotina antes de entregar

1. Execute `npm run lint:fix` e revise as alterações automáticas.
2. Execute `npm run lint` para comprovar que não restam violações.
3. Execute os testes/builds relevantes ao comportamento alterado.
4. Confira `git diff --check`, o diff e os arquivos novos.
5. Não crie commit nem faça push sem solicitação explícita.

O workflow `.github/workflows/lint.yml` prepara Node e Java 21, executa `npm ci` e `npm run lint` em pushes e pull requests. Ele não corrige arquivos nem cria commits. A exigência de aprovação desse check para merge depende da configuração de proteção de branches no GitHub.

O Prettier e o ESLint tornam a apresentação e certas regras de qualidade verificáveis. Nomes claros, responsabilidades, arquitetura, duplicação e correção das regras de negócio continuam exigindo revisão humana ou do agente.
