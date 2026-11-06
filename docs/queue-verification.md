# Verificação da fila

A WXN usa uma organização única, com isolamento funcional entre canais.

`bash scripts/verify-queue.sh` executa lint, sobe bancos de verificação em portas 14003/14004 e volumes
próprios, executa os testes existentes com `WXN_INTEGRATION_TEST=true` e o build do frontend.
O script para os containers ao terminar, sem remover volumes nem atingir os bancos de desenvolvimento.

As suítes existentes cobrem paginação, busca, atribuições HUMAN/BOT, transições e auditoria, contagem de
não lidas, cursores de mensagens, chave de desenvolvimento, envelope de eventos, providers e assinaturas
Twilio. `LocalFlowIntegrationTest` exercita persistência HTTP/Mongo/PostgreSQL e separação de canais.
A execução de integrações requer Docker e acesso às portas locais; compilar não comprova integração.

A chave `X-Dev-Api-Key` é obrigatória no REST interno e no CONNECT STOMP. O único destino de assinatura
é `/topic/conversations`. O envio de mensagens permanece via REST.
