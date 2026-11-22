# Serviço do bot (Python)

Serviço de IA do bot de atendimento. O backend chama este serviço por HTTP com a mensagem do cliente e recebe
uma decisão estruturada: responder ou encaminhar para um setor. O serviço não acessa os bancos do backend.

Stack: Python 3.14, FastAPI, Pydantic e o SDK oficial da Anthropic (Claude). Dependências travadas em `uv.lock`.

## Rodar localmente

Requer o [uv](https://docs.astral.sh/uv/). Na raiz do repositório, configure no `.env`:

- `BOT_API_KEY`: chave compartilhada com o backend, com pelo menos 32 caracteres;
- `ANTHROPIC_API_KEY`: chave da API da Anthropic, criada em platform.claude.com (a assinatura Claude Pro
  não inclui acesso à API);
- `BOT_MODEL`: modelo do Claude, por exemplo `claude-haiku-5-5`;
- `BOT_DB_PASSWORD`: senha do banco vetorial do bot (`openssl rand -hex 24`).

Pelo Docker Compose (perfil `ai`):

```bash
docker compose --profile ai up -d --build bot
```

Ou direto pelo uv, com as variáveis exportadas:

```bash
set -a; . ./.env; set +a
uv --directory bot run uvicorn wxn_bot.main:app --app-dir src --port 4005
```

## Janela de contexto e cache de prompt

O modelo recebe, nesta ordem: as instruções do bot com os setores (iguais entre chamadas), o histórico recente da
conversa e a mensagem atual. O histórico é cortado por `BOT_HISTORY_MAX_TOKENS` (estimado, padrão 4000); as
mensagens mais antigas saem primeiro. Há dois pontos de cache (`cache_control`): no fim das instruções e na
mensagem atual, que na chamada seguinte já faz parte do histórico. Em `run`, `cachedInputTokens` é o que veio do
cache e `cacheWriteInputTokens` o que foi gravado nele; numa conversa saudável cada chamada lê quase tudo do cache
e grava só o trecho novo. Nada que muda a cada chamada (data, hora, ids) pode entrar nas instruções.

## Base de conhecimento (RAG)

Os documentos da organização ficam em `bot/knowledge/*.md` (os atuais são **exemplos fictícios**: substitua pelo
conteúdo real). Cada seção (título com `#`) vira um trecho; seções longas são divididas por parágrafo. O banco é
um PostgreSQL com pgvector exclusivo do bot (`bot-db`), com schema versionado por Flyway em `bot/migrations`
(serviço `bot-db-migrate`, que roda antes do bot).

Depois de editar os documentos, reprocesse (a pasta é montada no container, não precisa de rebuild):

```bash
docker compose --profile ai exec bot python -m wxn_bot.knowledge_cli ingest
docker compose --profile ai exec bot python -m wxn_bot.knowledge_cli search "vocês entregam no sábado?"
```

Reprocessar substitui os trechos de cada arquivo; arquivos apagados têm os trechos removidos. A busca é
híbrida: por sentido (embeddings do modelo local `paraphrase-multilingual-MiniLM-L12-v2`, embutido na imagem,
sem internet) e por palavra (texto em português, sem acentos e sem palavras comuns), combinadas por Reciprocal
Rank Fusion. A telemetria do onnxruntime é desligada: nenhum texto sai do container. Trocar o modelo exige
rebuild (`--build-arg BOT_EMBEDDING_MODEL=...`) e reprocessar a base. O teste de busca com banco e modelo reais
é opcional: `BOT_TEST_DATABASE_URL=postgresql://bot:SENHA@127.0.0.1:4006/bot uv run pytest
tests/test_knowledge_search.py`.

## Agente: ferramentas e RAG

O bot é um agente com uma ferramenta, `buscar_conhecimento` (`tools.py`): antes de responder sobre a
organização, o modelo consulta a base (busca híbrida acima) e responde só com os trechos encontrados; se eles não
respondem à pergunta, encaminha para um humano. O próprio modelo decide quando buscar (uma saudação não busca).
O loop (`responder.py`) roda até `BOT_MAX_TOOL_STEPS` rodadas (padrão 3); na última as ferramentas ficam
indisponíveis e o modelo precisa decidir com o que já tem. A decisão final continua estruturada (`BotDecision`).
Ferramentas só leem dados: o encaminhamento é parte da decisão, executado pelo backend. Erro na busca (banco fora
do ar) volta ao modelo como erro da ferramenta, e ele encaminha. Em `run`, `toolCalls` lista as ferramentas
chamadas e `retrieved` os trechos usados. Uma resposta com busca faz duas chamadas ao modelo (cerca de 4.400
tokens de entrada, a maior parte lida do cache).

## Guardrails e fallback

Cada resposta passa por três etapas, em `pipeline.py`:

1. **Guardrails de entrada** (`guardrails.py`, sem chamar o modelo): mensagem acima de `BOT_MAX_INPUT_CHARS`
   (padrão 2000) vai para um humano; padrões conhecidos de manipulação ("ignore as instruções", "prompt do
   sistema"...) recebem uma recusa educada e a conversa continua com o bot.
2. **Cadeia de modelos**: `BOT_MODEL` e, se ele falhar (erro, recusa, resposta incompleta ou fora do formato),
   `BOT_FALLBACK_MODEL`. O próprio modelo também sinaliza tentativas de manipulação que os padrões não pegam
   (`manipulation_attempt`), com a mesma recusa.

Manipulação não é encaminhada: daria um atendente a quem tenta manipular e permitiria encher a fila. A recusa
termina com "Se preferir falar com uma pessoa, é só pedir", para o caso de falso positivo. 3. **Guardrails de saída**: resposta vazia, acima de 1600 caracteres ou com CPF ou número de cartão válidos é
descartada e passa para o próximo modelo.

Se nada servir, a decisão é encaminhar sem setor com uma mensagem fixa. O serviço só devolve erro (502) por
falhas fora do modelo. Em `run`, `fallbackStage` diz quem decidiu (`primary`, `secondary`, `fixed` ou
`blocked`), `guardrails` lista os que dispararam e `failures` as falhas por etapa. Os tokens somam todas as
tentativas, porque as reprovadas também são cobradas.

## Integração com o backend

Com `BOT_ENABLED=true`, o backend (pacote `com.wxn.chatbot.bot`) escuta `message.received` e, em segundo
plano:

1. atribui ao membro BOT toda conversa pendente e sem responsável (`IN_PROGRESS`);
2. chama `POST /reply` deste serviço com a mensagem, as últimas 50 mensagens de texto da conversa e os setores
   ativos, se o responsável for o BOT;
3. envia a resposta pelo serviço de mensagens, em nome do BOT, com `operationId` derivado da mensagem recebida
   (reprocessar o evento não duplica a resposta);
4. se o bot decidiu encaminhar (`needsHuman`), devolve a conversa à fila: `PENDING`, sem responsável, com o
   setor escolhido, `waitingForAgentSince` no momento do encaminhamento (início do SLA) e a atividade
   `BOT_HANDOFF` com o motivo no histórico;
5. se o bot falhar (fora do ar, timeout ou resposta inválida), envia uma mensagem fixa de encaminhamento e
   devolve a conversa à fila sem setor;
6. grava a execução na coleção `bot_runs` do Mongo: resultado (`REPLIED`, `HANDED_OFF`, `FALLBACK_HANDOFF`,
   `DISCARDED` ou `FAILED`), decisão, modelo, `requestId` da Anthropic, tokens, latência e código de erro.

Conversas com atendente humano não são tocadas. Se um humano assumir a conversa enquanto o bot gera a resposta,
ela é descartada. Conversa encaminhada no ciclo atual não volta para o bot; ao ser encerrada e reaberta, o bot
volta a atender. Falhas do bot ficam registradas e não interrompem o recebimento de mensagens.

Variáveis do backend no `.env`:

- `BOT_ENABLED`: liga o bot (padrão `false`);
- `BOT_MEMBER_ID`: id de um membro ativo do tipo BOT;
- `BOT_API_KEY`: a mesma chave deste serviço;
- `BOT_SERVICE_URL` e `BOT_TIMEOUT_SECONDS`: opcionais; no compose a URL já aponta para `http://bot:8000`.

Com o bot ligado e a configuração incompleta, o backend não inicia.

### Teste manual pelo frontend

1. Crie o membro BOT uma vez, com uma sessão de administrador: `POST /api/members` com
   `{"name": "Bot WXN", "type": "BOT", "status": "ACTIVE"}`. Anote o `id` devolvido em `BOT_MEMBER_ID`.
2. No `.env`, defina `BOT_ENABLED=true` e `SPRING_PROFILES_ACTIVE=dev,legacy` (o canal MOCK e o simulador só
   existem com o perfil `legacy`). Crie um canal do tipo MOCK com telefone, se ainda não houver.
3. Suba tudo, incluindo o bot: `docker compose --profile ai up -d --build`.
4. No frontend, abra um canal do tipo MOCK e use o **Simulador local**: digite uma mensagem como cliente.
5. A conversa aparece atribuída ao BOT, com a resposta do Claude. Para ver o registro:
   `docker compose exec mongodb mongosh "$MONGO_DB" --eval 'db.bot_runs.find().sort({createdAt: -1}).limit(1)'`.

## Testes e lint

Os testes substituem o modelo por um falso e não chamam a API da Anthropic.

```bash
npm run test:python
npm run lint:python
```

## Contrato

`GET /health` é público e responde `{"status": "ok"}`.

`POST /reply` exige o header `X-Api-Key` com a chave do serviço.

Requisição:

```json
{
  "conversationId": "665f1a2b3c4d5e6f7a8b9c0d",
  "channelId": 1,
  "inboundMessageId": "wamid.123",
  "message": "Qual o horário de vocês?",
  "history": [{ "direction": "INBOUND", "text": "Olá", "sentAt": "2026-11-21T10:00:00Z" }],
  "departments": [{ "id": 3, "name": "Financeiro", "description": "Pagamentos e boletos" }]
}
```

Resposta:

```json
{
  "reply": "Funcionamos de segunda a sexta, das 8h às 18h.",
  "intent": "horario_atendimento",
  "needsHuman": false,
  "departmentId": null,
  "handoffReason": null,
  "run": {
    "model": "nome-do-modelo",
    "inputTokens": 412,
    "outputTokens": 38,
    "cachedInputTokens": 0,
    "cacheWriteInputTokens": 1117,
    "historyMessages": 2,
    "latencyMs": 1240,
    "requestId": "req_011Ch..."
  }
}
```

Quando `needsHuman` é `true`, `handoffReason` é obrigatório. `departmentId`, quando informado, precisa ser um
dos setores recebidos; `null` significa encaminhar sem setor (guardrail, fallback ou nenhum setor cadastrado). `run.requestId` identifica a chamada nos Logs
do Console da Anthropic.

| Status | Quando                                                                       |
| ------ | ---------------------------------------------------------------------------- |
| 200    | Decisão válida                                                               |
| 401    | `X-Api-Key` ausente ou incorreta                                             |
| 422    | Corpo fora do contrato                                                       |
| 502    | Falha do Claude ou decisão inválida (por exemplo, resposta acima de 1600 c.) |

Teste manual com o serviço rodando:

```bash
curl -s -X POST http://localhost:4005/reply \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: SUA_CHAVE" \
  -d '{"conversationId":"c-1","channelId":1,"inboundMessageId":"m-1","message":"Qual o horário de vocês?"}'
```
