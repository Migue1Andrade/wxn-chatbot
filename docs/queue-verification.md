# Validação da entrega: visualizar fila de conversas

Validação de 23/10/2026 do escopo de persistência e serviços de negócio, com requisições HTTP reais a uma instância Spring Boot iniciada pelos testes, PostgreSQL 17 e MongoDB 8.

## Resultado

- 42 testes Java aprovados, zero falhas, zero erros e zero ignorados.
- 15 testes de integração: 14 cenários da fila e 1 fluxo geral de persistência/WhatsApp Mock.
- 7 testes Node aprovados, incluindo regressões de busca e contagem de não lidas.
- Empacotamento Spring Boot, build Vite, validação Compose e `git diff --check` aprovados.

A execução final usou compilação limpa com Temurin Java 21.0.6, a versão principal declarada pelo projeto. A suíte também passou no Java 23.0.2 disponível no ambiente.

## Cobertura dos cenários

| Cenário | Evidência automatizada |
| --- | --- |
| PENDING, IN_PROGRESS, CLOSED e documentos legados OPEN | `ConversationQueueIntegrationTest.filtersIncludeLegacyStatesAndNeverLeakAnotherCompany` |
| Responsável HUMAN/BOT, sem responsável e combinação com status | Mesmo teste de filtros |
| Ordenação por atividade e desempate por `_id`, páginas sem duplicatas, última página e página vazia | `paginationIsStableWithTimestampTiesAndHandlesEmptyAndLastPages` |
| Padrão de 30 itens, limite de 200 e empresa sem conversas | `defaultPageSizeIsThirtyAndOtherTenantCanHaveEmptyQueue` e `invalidQueriesAndForeignOrInvalidReadersAreRejected` |
| Nome sem distinção de maiúsculas, telefone formatado, identificador externo e ObjectId | `searchMatchesNamesFormattedPhonesExternalIdsAndLiteralPunctuation` |
| Busca literal de pontuação e prevenção de regex telefônica vazia | Mesmo teste de busca, `ConversationOperationsTest` e `frontend/tests/chat-state.test.mjs` |
| Limites inválidos, status inválido, filtros incompatíveis e leitor de outra empresa, BOT ou inativo | `invalidQueriesAndForeignOrInvalidReadersAreRejected` |
| Chave HTTP ausente/inválida, empresa configurada no servidor e empresa enviada no corpo Mock | `realHttpGuardRequiresKeyAndRejectsCompanyOutsideServerContext` e `DevelopmentApiGuardTest` |
| Acesso direto a conversa, mensagens e leitura de outra empresa | `directConversationMessageAndReadAccessRemainTenantScoped` |
| Assumir, atribuir, transferir, encerrar e reabrir; responsável inativo; repetição idempotente; envio bloqueado em CLOSED | `managementPersistsTransitionsAuditsAndIdempotency` |
| Versão obsoleta e atribuição simultânea com apenas um vencedor e um evento | `managementPersistsTransitionsAuditsAndIdempotency` e `concurrentAssignmentsHaveOnlyOneWinnerAndOneAuditEvent` |
| Transferências HUMAN/HUMAN, HUMAN/BOT, BOT/BOT e BOT/HUMAN, com membros anteriores/novos na auditoria | `transfersSupportHumanHumanHumanBotBotBotAndBotHumanWithAuditedMembers` |
| Nova mensagem reabre conversa fechada, limpa responsável e mantém idempotência | `newInboundReopensClosedConversationAndDuplicateDoesNotReopenAgain` |
| Não lidas por timestamp + ID, avanço monotônico e concorrente, exclusão de OUTBOUND e outra empresa | `unreadBoundaryUsesTimestampAndIdIsMonotonicAndExcludesOutboundAndOtherTenants` |
| Estado de leitura independente por HUMAN e sem alterar status de entrega | `readStateIsIndependentPerHumanAndDoesNotChangeMessageDeliveryStatus` |
| Cursor do histórico com 65 mensagens de mesmo timestamp, sem perdas ou duplicações | `messageCursorWalksEveryMessageWithTimestampTiesWithoutDuplicates` |
| Validação no serviço mesmo sem Controller e tratamento do item extra de paginação | 6 testes de `ConversationQueryServiceTest` |
| Flyway V1–V5, persistência em ambos os bancos, restrições relacionais entre empresas, Mock idempotente, fixtures assinadas de webhook | `LocalFlowIntegrationTest` |
| Envelope de eventos, restrição de destinos STOMP, normalização e progressão de status | Suítes existentes `ConversationEventsTest`, `DevelopmentStompInterceptorTest`, `ProviderTest` e `TwilioIngressTest` |

As fixtures relacionais são criadas via HTTP. Documentos controlados de conversa/mensagem também são inseridos diretamente no MongoDB para verificar empates de timestamp e compatibilidade com legado. As consultas, transições e fronteiras de leitura passam pela API, pelos serviços e pelos repositories reais.

O contexto multiempresa ampliado usa um spy de `DevelopmentAccess` exclusivamente nos testes para exercitar as regras de domínio. O teste específico do guard restringe novamente a empresa permitida e verifica respostas 401/403 por HTTP. A configuração de produção não foi alterada.

## Reprodução

Com Docker disponível, dependências instaladas e `.env` configurado, execute na raiz:

```bash
bash scripts/verify-queue.sh
```

Se necessário, informe o JAR da versão de Mockito resolvida pelo projeto:

```bash
MOCKITO_AGENT_JAR=/caminho/mockito-core-<versao>.jar bash scripts/verify-queue.sh
```

O script usa o projeto Compose `wxn-queue-review`, portas 14003/14004 e volumes próprios. Ativa os testes de integração, executa compilação limpa, todos os testes e builds, e para os containers ao sair. As fixtures permanecem nos volumes de teste e cada execução cria novas empresas para isolar seus resultados. Os dados de desenvolvimento não são utilizados.

Relatórios detalhados Java ficam em `target/surefire-reports/`. A suíte é opt-in: rodar apenas `mvn test` sem `WXN_INTEGRATION_TEST=true` ignora os testes integrados.

## Limites da evidência

Os resultados validam os cenários descritos da entrega, sem representar garantia absoluta de ausência de defeitos ou percentual de cobertura de linhas. A fila é ordenada por atividade mais recente; prioridade e FIFO não foram requisitos fornecidos. A validação não inclui teste de carga, automação visual completa do navegador ou envio real à Twilio. A autenticação definitiva continua fora do escopo desta etapa.
