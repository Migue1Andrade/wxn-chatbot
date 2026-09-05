# Projeto WXN Chatbot

Este projeto está na etapa de preparação da base técnica para o desenvolvimento do chatbot e do dashboard da WXN.

## Stack

**Backend**

- Java 21
- Spring Boot
- Maven
- Spring Data JPA
- Hibernate
- Flyway

**Frontend**

- AngularJS 1.8.3
- Vite
- Node.js e npm

**Bancos de dados**

- PostgreSQL
- MongoDB

**Ambiente**

- Docker Compose
- Git/GitHub

## Estrutura

```text
src/
├── main/
│   ├── java/
│   └── resources/

frontend/
├── src/
├── package.json
└── vite.config.js

compose.yaml
pom.xml
.env.example
AGENTS.md
README.md
```

O backend fica na raiz do repositório, em `src/`, e é executado pelo Maven. O dashboard fica em `frontend/`.

## Banco de Dados

O PostgreSQL é o banco relacional principal. O schema é controlado pelo Flyway e, por enquanto, inclui as estruturas iniciais de `companies` e `users`.

O MongoDB armazena os dados conversacionais. As estruturas iniciais são `conversations`, `messages` e `message_logs`.

## Multi-tenancy

- `companies` representa cada empresa, ou tenant, do sistema.
- Um `user` pertence a uma `company`.
- Os documentos conversacionais do MongoDB possuem `companyId`.
- `companyId` será usado para o isolamento lógico entre empresas.
- O PostgreSQL continua sendo a fonte principal das empresas.

## Ambiente de Desenvolvimento

Pré-requisitos:

- Java 21
- Maven
- Node.js e npm
- Docker Desktop com Docker Compose
- IntelliJ IDEA para o backend ou VS Code para o frontend

PostgreSQL e MongoDB são executados localmente pelo Docker Compose. Backend e frontend continuam sendo executados diretamente na máquina durante o desenvolvimento.

## Variáveis de Ambiente

Crie o arquivo local a partir do exemplo:

```bash
cp .env.example .env
```

As variáveis usadas atualmente são:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `POSTGRES_PORT`
- `POSTGRES_URL`
- `MONGO_DB`
- `MONGO_PORT`
- `MONGODB_URI`
- `VITE_API_BASE_URL`

Os valores reais não devem ser versionados. Use o arquivo `.env`, que já está ignorado pelo Git.

## Execução Local

1. Configure o arquivo `.env`:

   ```bash
   cp .env.example .env
   ```

2. Inicie PostgreSQL e MongoDB:

   ```bash
   docker compose up -d
   ```

3. Exporte as variáveis e execute o backend na raiz do repositório:

   ```bash
   set -a
   source .env
   set +a
   mvn spring-boot:run
   ```

4. Em outro terminal, execute o frontend:

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

Para encerrar os bancos locais:

```bash
docker compose down
```

## Build

Backend:

```bash
mvn verify
```

Frontend:

```bash
cd frontend
npm run build
```

O build do frontend é gerado em `frontend/dist/`.

## Ambiente de Deploy

O ambiente local está preparado para ser reproduzido com Docker Compose. O backend pode ser executado como uma aplicação Spring Boot e o frontend possui build de produção pelo Vite.

PostgreSQL e MongoDB são dependências externas, e as configurações são fornecidas por variáveis de ambiente. O projeto está preparado para uma futura hospedagem em cloud, mas ainda não há provedor cloud definido nesta etapa.

