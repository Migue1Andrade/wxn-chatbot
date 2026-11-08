# Bot com n8n

## Estado atual

O n8n é executado localmente pelo perfil `bot` do Docker Compose:

- Interface: `http://localhost:5678`
- Workflow: `wxn-chatbot`
- Endpoint publicado: `POST /webhook/chat`
- Autenticação: cabeçalho `X-Api-Key`

O workflow atual valida a comunicação e responde com um contrato provisório:

```json
{
  "reply": "Ainda estou sendo configurado. Vou encaminhar seu atendimento.",
  "intent": "humano",
  "needsHuman": true
}
```

OpenAI, memória, RAG e integração com o backend ainda não estão implementados.

## Inicialização local

Configure o `.env` local sem versioná-lo. Ele deve conter uma chave estável para
`N8N_ENCRYPTION_KEY`; essa chave protege as credenciais armazenadas pelo n8n.

Valide e inicie o serviço:

```bash
docker compose config --quiet
docker compose up -d n8n
```

Confira o serviço:

```bash
docker compose ps n8n
```

Na primeira execução, acesse `http://localhost:5678` e crie a conta de proprietário
do n8n. Essa conta é independente dos usuários do WXN.

## Contrato inicial

O backend deverá enviar:

```json
{
  "channelId": 1,
  "conversationId": "665f1a...",
  "message": "Quais são os horários?"
}
```

Headers:

```text
Content-Type: application/json
X-Api-Key: <chave da credencial do webhook>
```

O backend atual usa `channelId`; não enviar `companyId` neste contrato.

O n8n responde:

```json
{
  "reply": "Vou encaminhar seu atendimento.",
  "intent": "humano",
  "needsHuman": true
}
```

O backend continua responsável por persistir a mensagem, enviar a resposta pelo
provider e registrar os logs. O bot só será chamado quando a conversa não tiver
atendente humano atribuído.

## Teste do endpoint publicado

Use a chave salva na credencial `Header Auth` do n8n. Nunca registre a chave no
repositório ou em documentação:

```bash
curl -i -X POST http://localhost:5678/webhook/chat \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: SUA_CHAVE" \
  -d '{"channelId":1,"conversationId":"c-123","message":"ola"}'
```

O workflow deve retornar HTTP 200 com `reply`, `intent` e `needsHuman`.

## Retomada em outra máquina

O workflow e as credenciais ficam no volume Docker `n8n_data` e não são
versionados automaticamente. Exporte o workflow pela interface do n8n e salve
o JSON em `n8n/workflows/wxn-chatbot.json`. Revise o arquivo exportado antes de
versioná-lo e confirme que ele não contém credenciais ou segredos.

Na nova máquina:

1. Faça checkout da branch.
2. Crie o `.env` local a partir do `.env.example`.
3. Gere uma nova `N8N_ENCRYPTION_KEY` estável para essa instalação.
4. Inicie o n8n e crie a conta de proprietário.
5. Importe o workflow JSON.
6. Recrie a credencial `X-Api-Key`.

Credenciais não devem ser copiadas para o Git nem compartilhadas em mensagens.

## Próximas etapas

1. Conectar o modelo OpenAI por uma credencial do n8n.
2. Normalizar e validar a entrada.
3. Adicionar memória por conversa.
4. Implementar a base de conhecimento e o RAG.
5. Configurar fallback para erro e timeout.
6. Criar o cliente n8n no backend e ligá-lo ao evento `message.received`.
