# Avaliação funcional do RAG

O harness opt-in em `KnowledgeAdminIntegrationTest#evaluatesFixedDeterministicRetrievalCorpusAcrossTextVectorAndHybridModes`
compara recuperação textual, vetorial e híbrida sobre artigos fictícios de pós-venda em PostgreSQL/pgvector.
Os vetores são bases unitárias determinísticas, definidos pelo próprio teste. Eles servem para verificar o
filtro de provedor/modelo/dimensão, o limite de similaridade e a fusão das listas; não medem qualidade
semântica de um modelo real.

## Dados fixos

Quatro artigos publicados cobrem garantia de fábrica, revisão preventiva, assistência técnica e abertura de
chamado. Cada artigo contém um trecho e recebe, respectivamente, os vetores `[1,0,0,0]`, `[0,1,0,0]`,
`[0,0,1,0]` e `[0,0,0,1]`, com identidade `evaluation-fixture/deterministic-v1`.

| Tipo          | Pergunta de fixture                                            | Fonte esperada      |
| ------------- | -------------------------------------------------------------- | ------------------- |
| Literal       | cobertura garantia fábrica                                     | Garantia de fábrica |
| Paráfrase     | produto apresentou defeito após a compra, qual proteção existe | Garantia de fábrica |
| Termo exato   | abrir chamado suporte número série                             | Abertura de chamado |
| Sem cobertura | segunda via boleto condomínio                                  | Nenhuma             |

O modo vetorial usa uma consulta lexical opaca e só compara vetor com identidade/dimensão iguais; o modo
híbrido usa a pergunta literal e o mesmo vetor determinístico. O limite mínimo de similaridade é `0.9`, de
modo que a pergunta sem cobertura (vetor equidistante) não pode receber um vizinho vetorial como resposta.

## Métricas e execução

O teste mede `hit@1` e `hit@5` nas três perguntas cobertas, acerto de ausência na pergunta sem cobertura e
latência SQL p50/p95 em microssegundos. Cada pergunta/modo tem 20 medições; o hit é contado uma vez por
pergunta, então o denominador é três para hit e um para ausência correta. As latências são diagnósticas e
dependem do hardware, cache e carga do banco; não são metas de produção. O teste exige hit em `@1` e `@5`
para vetor e híbrido e ausência correta para os três modos.

Uma execução local isolada registrou: texto `hit@1=2/3`, vetorial `3/3`, híbrido `3/3`; ausência correta
`1/1` nos três modos. Latência p50/p95 em microssegundos: texto `440/1023`, vetorial `436/1080`, híbrido
`436/1092`. São observações funcionais de uma execução local, não uma comparação de qualidade semântica
nem previsão de desempenho de produção.

Para reproduzir, configure `POSTGRES_URL` para um banco **isolado** recém-migrado por Flyway com PostgreSQL
17 e pgvector, `MONGODB_URI` para MongoDB isolado, e `WXN_INTEGRATION_TEST=true`. O teste liga somente um
servidor HTTP fictício local já usado pela fixture da classe; não chama provedor externo nem WhatsApp.

```bash
mvn -Dtest=KnowledgeAdminIntegrationTest#evaluatesFixedDeterministicRetrievalCorpusAcrossTextVectorAndHybridModes test
```

Para avaliar um provedor real, primeiro repita o mesmo corpus e as mesmas perguntas em um banco descartável,
substitua apenas os vetores determinísticos pela saída do provider configurado e registre a identidade
exata de provedor/modelo/dimensão. Mantenha a mesma política de similaridade e limite. Compare `hit@k`,
ausência correta e latência lado a lado; não use conversas reais nem interprete este fixture determinístico
como validação semântica.

## Baseline real local: EmbeddingGemma

Uma execução isolada do piloto (`Ollama 0.11.10`, `embeddinggemma:300m`, dimensão `768`) mediu uma única
pergunta de pós-venda: modo híbrido encontrou a fonte esperada em `hit@1` e `hit@5`, sem lacuna; consulta
SQL levou `25.104 µs`. A medição foi um smoke funcional (`n=1`), não demonstra qualidade nem deve ser
comparada com a avaliação determinística.

Uma execução ampliada posterior, com o corpus e as quatro perguntas fixas acima, registrou texto `hit@1/@5
2/3`, ausência correta `1/1`; vetor e híbrido `hit@1/@5 3/3`, ausência correta `0/1` (um falso positivo cada)
no limiar `0,25`. A geração dos oito embeddings levou `8,814 s` no total e o SQL ficou abaixo de `5,5 ms`
por consulta nessa amostra. O falso positivo vetorial indica que `0,25` não separou a pergunta sem cobertura;
nenhum limiar alternativo foi validado nesta amostra, portanto não há recomendação de corte. Use abstenção
operacional até validar um limiar em perguntas separadas de calibração e validação; não ajuste no mesmo
conjunto reportado.

## Validação separada com EmbeddingGemma

O limiar candidato de `0,721237` é o ponto médio entre a maior similaridade da lacuna de
calibração (`0,713463`) e a menor similaridade entre as perguntas com fonte (`0,729010`).
Consultamos seis outras perguntas no mesmo corpus fictício, sem reajustar o corte.

| Modo     | Limiar                 | Fonte em 1º | Fonte nas 5 primeiras | Ausência correta |
| -------- | ---------------------- | ----------- | --------------------- | ---------------- |
| Textual  | Independente do limiar | 1/4         | 1/4                   | 2/2              |
| Vetorial | 0,25                   | 3/4         | 4/4                   | 0/2              |
| Híbrido  | 0,25                   | 4/4         | 4/4                   | 0/2              |
| Vetorial | 0,721237               | 3/4         | 3/4                   | 1/2              |
| Híbrido  | 0,721237               | 4/4         | 4/4                   | 1/2              |

O candidato ainda aceita a pergunta sobre cobertura de dano por queda (`0,748834`).
Um corte que a descarte também exclui a fonte sobre assistência (`0,724589`), que a busca
textual não encontrou. Esta amostra não sustenta ativação automática com um limiar global.

Para repetir, use um banco PostgreSQL com pgvector e MongoDB isolados, com base de conhecimento vazia.
Configure o Ollama local com `embeddinggemma:300m` antes de executar:

```bash
WXN_INTEGRATION_TEST=true WXN_REAL_EMBEDDING_TEST=true \
AUTOGUIA_EMBEDDING_ENDPOINT=http://127.0.0.1:14134 \
AUTOGUIA_EMBEDDING_PROVIDER=ollama AUTOGUIA_EMBEDDING_MODEL=embeddinggemma:300m \
AUTOGUIA_EMBEDDING_DIMENSION=768 mvn -Dtest=RealEmbeddingPilotIntegrationTest test
```

O teste grava artigos e um administrador fictício no banco isolado. A saída separa o tempo de
geração dos vetores do tempo das consultas SQL. O adaptador acrescenta `/v1/embeddings` à URL base.

## Roteiro antes/depois da publicação

1. Antes: consulte uma pergunta do corpus e registre modo, IDs/versões/fontes e resultado da recuperação.
2. Crie e publique uma revisão em ambiente isolado; aguarde o trabalho de indexação concluir.
3. Repita a pergunta idêntica e confirme que a fonte publicada vigente e seu trecho aparecem.
4. Crie uma revisão nova sem publicar; confirme que ela ainda não substitui a fonte da busca.
5. Arquive o artigo; confirme que a fonte deixa de aparecer e que uma indexação atrasada não a reativa.

Esse roteiro avalia recuperação e ciclo editorial. A prévia de resposta gerada é uma operação separada e
não envia nem atualiza conversas.

## Prévia de resposta isolada

`POST /api/admin/knowledge/preview` mantém a busca como padrão: sem `generate` ou com `generate: false`,
`generatedAnswer` é nulo e `generationStatus` é `NOT_REQUESTED`. Para solicitar geração explicitamente:

```json
{
  "question": "Como solicitar assistência?",
  "limit": 5,
  "generate": true
}
```

A resposta inclui sempre `mode` e `sources`; também informa `generationStatus` (`GENERATED`, `NO_SOURCES`,
`NOT_CONFIGURED` ou `UNAVAILABLE`) e `generatedAnswer`. Sem fontes recuperadas, o gerador não é chamado.
Indisponibilidade ou falta de configuração deixa a resposta vazia; os trechos ficam visíveis como fontes,
sem convertê-los em uma resposta automática.

O gerador HTTP compatível com `/v1/chat/completions` fica desligado por padrão. Para uma fixture ou
ambiente de demonstração isolado, configure `AUTOGUIA_GENERATION_ENABLED=true`,
`AUTOGUIA_GENERATION_ENDPOINT`, `AUTOGUIA_GENERATION_MODEL` e, quando exigido pelo serviço,
`AUTOGUIA_GENERATION_API_KEY`; `AUTOGUIA_GENERATION_CONNECT_TIMEOUT` e
`AUTOGUIA_GENERATION_TIMEOUT` controlam os limites de conexão e resposta. A chamada externa recebe somente
a pergunta de prévia e títulos/trechos recuperados, nunca IDs ou histórico da conversa. Nunca habilite ou
configure credenciais reais para executar os testes locais; o teste usa servidor HTTP fictício em loopback.

## Teste de respostas com a base da WXN Tecnologia

`RealWxnGroundingTest` usa os artigos publicados da WXN, o repositório de busca híbrida e
dois modelos reais no Ollama. O PostgreSQL deve estar acessível com uma conexão somente
leitura. Configure `WXN_GROUNDING_DB_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`,
`WXN_GROUNDING_OLLAMA_URL` com a URL base do Ollama e `WXN_GROUNDING_MODEL` com o nome
do modelo de seleção. O modelo dos vetores continua sendo `embeddinggemma:300m`.

```bash
WXN_REAL_GROUNDING_TEST=true mvn -Dtest=RealWxnGroundingTest test
```

O teste falha se a busca degrada ou se uma pergunta com cobertura é encaminhada. Também
falha se uma pergunta sem cobertura recebe uma resposta. Ele usa perguntas sintéticas e
não dispara mensagens. `OpenBotSupportServiceTest` testa separadamente o envio simulado,
o encaminhamento, solicitações duplicadas e o contrato do webhook legado.

Na operação com RAG, o modelo indica uma opção da lista de artigos e uma frase do artigo
escolhido. O backend envia a frase publicada pelo fluxo normal de mensagens. Sem uma
escolha válida ou com erro do provedor, encaminha à equipe. Esse fluxo dispensa o callback
do webhook para o atendimento com RAG. O fluxo legado continua usando o webhook.

No teste de aceite com `qwen2.5:7b`, Ollama 0.11.10 em macOS e os dez artigos publicados,
passaram os sete cenários abaixo:

| Cenário                                      | Resultado                                       |
| -------------------------------------------- | ----------------------------------------------- |
| Ligar o software de gestão a outros sistemas | Resposta do artigo de integração com ERP e APIs |
| Trocar de plataforma preservando os dados    | Resposta do artigo de migração                  |
| Consultoria para TOTVS Protheus              | Resposta do artigo de Protheus                  |
| Preço mensal de hospedagem                   | Encaminhamento à equipe                         |
| Venda de pneus                               | Encaminhamento à equipe                         |
| Prazo garantido de migração                  | Encaminhamento à equipe                         |
| Pedido explícito de atendente                | Encaminhamento direto à equipe                  |

O artigo de integração aparecia em nono lugar na busca vetorial para a primeira
pergunta. O atendimento agora avalia até dez fontes. O teste usou os artigos existentes
com acesso somente leitura. O limite por chamada do gerador é configurável e o padrão
é 45 segundos. Na repetição direta com o Ollama, as seis consultas que usaram o modelo
levaram de 11,9 a 15,7 segundos por cenário. A função segue desabilitada por padrão.
