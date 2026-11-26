# Recuperação de senha

A API expõe `POST /api/auth/password-reset/request` com `{ "email": "conta@example.invalid" }` e retorna
`202 Accepted` com o mesmo resultado para e-mails cadastrados e não cadastrados. A mensagem também é
genérica. `POST /api/auth/password-reset/confirm` recebe `{ "token": "...", "password": "..." }` e retorna
`204 No Content`. Token inválido, expirado ou usado retorna `400` com mensagem uniforme.

O token tem 256 bits aleatórios, só seu SHA-256 fica no PostgreSQL e expira em 20 minutos. Um novo pedido
invalida tokens anteriores da conta. A confirmação bloqueia a conta no PostgreSQL, consome o token uma
única vez, troca a senha com o BCrypt já utilizado pelo login e incrementa `credential_version` na mesma
transação; tokens remanescentes também são invalidados. Cada IP pode solicitar até 8 e-mails e tentar 12
confirmações por hora; cada endereço pode solicitar até 3 e-mails por hora. As chaves de limite ficam
armazenadas como hash e os endereços não são registrados em log.

## SMTP

O link usa o formato `http://localhost:4002/#!/reset-password?token=...` para que o token fique no fragmento
da SPA, não seja enviado ao servidor web e seja lido por AngularJS. Configure `PASSWORD_RESET_PUBLIC_URL`
com uma URL absoluta terminando exatamente em `/#!/reset-password`; URLs antigas sem esse fragmento são
rejeitadas na inicialização para evitar links que abrem no login.

O Compose inclui Mailpit para captura local (`http://localhost:8025`, SMTP em `localhost:1025`). A aplicação
usa `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH` e `SMTP_STARTTLS`. O exemplo
de Compose usa `SMTP_HOST=mailpit`; execução local do backend fora do Compose usa `SMTP_HOST=localhost`.
Para SMTP real, defina esses valores fora do repositório. `PASSWORD_RESET_PUBLIC_URL` precisa ser uma URL absoluta
HTTPS confiável para a página de redefinição em produção (HTTP só é aceito em localhost no dev); o backend nunca usa `Host` nem `Origin` para construí-la.
Configure também `PASSWORD_RESET_FROM`. O padrão do Compose é o Mailpit e remetente fictício local.

O envio é enfileirado após o commit em executor limitado. Falhas SMTP não alteram a resposta `202` nem
registram endereço, token ou conteúdo; a mensagem pode não chegar quando o relay falha ou a fila está cheia.

## Verificação isolada

Use um projeto Compose e volumes próprios, com portas livres, sem tocar no ambiente principal. Com os
bancos de teste e Mailpit ativos, configure `POSTGRES_PORT`, `MONGO_PORT`, `POSTGRES_URL`, `MONGODB_URI`,
`SMTP_HOST=localhost`, `SMTP_PORT` e `PASSWORD_RESET_PUBLIC_URL`, e execute:

```bash
WXN_INTEGRATION_TEST=true ./mvnw -Dtest=PasswordResetIntegrationTest test
```

O teste cria uma conta e e-mail `example.invalid`, lê a mensagem somente da caixa local, confirma o uso
único e expiração, verifica que a sessão antiga perdeu acesso e que a senha nova autentica. Não use um
servidor de SMTP de produção nesses testes.
