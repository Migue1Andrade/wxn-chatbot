# Arquitetura atual

## Escopo verificado

O repositório atual é um monólito de organização única. A regra de `companyId` existente em
`AGENTS.md` representa uma direção futura; os modelos atuais não implementam isolamento por tenant.
Não adicionar suporte parcial que pareça multi-tenant sem uma decisão arquitetural e migrations
completas.

## Componentes

```text
AngularJS 1.8.3 + Vite
        │ REST / STOMP
        ▼
Spring Boot 3.5 / Java 21
  ├─ Controllers: contratos HTTP e validação
  ├─ Services: autorização, regras e coordenação
  ├─ Repositories: MongoDB/PostgreSQL
  └─ Providers: Evolution, Mock, Twilio e Z-API
        │
        ├─ PostgreSQL + Flyway: usuários, membros, setores e configurações relacionais
        ├─ MongoDB: conversas, mensagens, eventos, leituras e auditoria conversacional
        └─ Evolution API v2.3.7 / Baileys: WhatsApp conectado
```

## Mensagens e callbacks

O envio é iniciado por REST, passa pelo `MessageService`/provider e cria uma mensagem local com
idempotência por canal e ID de operação. O callback Evolution entra em
`/api/webhooks/evolution`, é autenticado, gravado no `webhook_inbox`, processado uma vez e publicado
via `ConversationEventPublisher` para STOMP.

O ID interno MongoDB nunca é substituído pelo ID externo do WhatsApp. A correlação usa canal e
`externalMessageId`; alterações remotas usam eventos de atualização/exclusão e preservam o documento
da mensagem como histórico.

Mensagens recebidas não são filtradas por uma lista fixa de telefones. O webhook continua exigindo a
autenticação configurada, e a aplicação mantém validação de payload, correlação por canal e idempotência.

Operações de edição, reação e leitura possuem estado próprio em `pendingMutation`. Quando o provider
confirma por callback, o estado vira `CONFIRMED`. Se não houver confirmação após 120 segundos, vira
`TIMEOUT`, o histórico da operação é atualizado e o evento é publicado, evitando loading infinito.
`TIMEOUT` não afirma que a operação falhou: a interface orienta verificar o WhatsApp antes de repetir.

## WebSocket

O backend publica envelopes de conversa no tópico permitido ao usuário. O frontend atualiza a entidade
existente por ID/versão e mantém o REST como fonte de recuperação após reconexão.

## Persistência e evolução

O schema PostgreSQL é controlado somente por Flyway; migrations aplicadas não devem ser reformatadas ou
alteradas. Índices MongoDB são criados pela configuração de inicialização. Alterações de documento que
precisem ser compatíveis com histórico devem aceitar campos ausentes e preservar dados antigos.

## Limitações conhecidas

- A integração ativa foi validada por build, fixtures e testes de mapper, mas não por uma suíte contra
  uma instância Evolution real nesta sessão.
- O contrato de edição/exclusão depende da versão instalada da Evolution e dos callbacks disponíveis;
  `TIMEOUT` é deliberadamente honesto quando a confirmação não chega.
- Não há autenticação produtiva completa nem isolamento multi-tenant concluído.
- A validação visual depende de navegador e Docker disponíveis no ambiente de execução.
