# RAG dinâmico do AutoGuia

## Objetivo

Permitir que administradores publiquem artigos de pós-venda e que o AutoGuia responda com base na versão vigente. A equipe acompanha as fontes recuperadas e transforma dúvidas sem cobertura em novos artigos revisados.

## 1. Persistência e publicação

- Usar PostgreSQL 17 com pgvector no Compose e uma nova migration Flyway. Testar inicialização com volume existente e volume vazio. Preservar migrations anteriores e dados.
- Armazenar artigos, versões e trechos separadamente. Registrar título, conteúdo, categoria, versão, autor e datas. Cada trecho guarda a posição, o texto, o vetor e a identidade do modelo de embeddings.
- Estados editoriais: rascunho, publicado e arquivado. Uma revisão pode ficar em rascunho enquanto a edição anterior continua publicada. Publicar somente após indexar todos os trechos da nova versão. Trocar a versão publicada em uma transação.
- Processar publicação de forma assíncrona com trabalho persistente, tentativas limitadas e erro visível. A publicação deve ser idempotente e uma tentativa antiga não deve sobrescrever uma revisão mais recente. Arquivar retira o artigo da busca.
- Proteger gestão por conta administrativa e reutilizar sessão e auditoria existentes. O projeto atual opera com uma organização. Não apresentar a solução como multiempresa.

## 2. Recuperação e resposta

- Semear artigos fictícios de pós-venda: garantia, revisões, assistência e abertura de chamado. Dividir por títulos/parágrafos, com limite de tamanho e sobreposição ajustada pelos testes.
- Configurar um provedor de embeddings por ambiente. Usar o mesmo modelo e dimensão para artigos e consultas. Tratar troca de modelo como reindexação. Guardar chaves apenas no ambiente seguro.
- Combinar busca em português no PostgreSQL e proximidade vetorial com pgvector. Fundir as posições das duas listas, deduplicar trechos e limitar o contexto. Usar busca vetorial exata no piloto e medir antes de criar índice aproximado.
- No backend do AutoGuia, enviar trechos e referências ao webhook existente, preservando assinatura, requestId, histórico restrito à conversa e callback. Tratar artigo como fonte de conteúdo e manter instruções do bot separadas.
- Quando os trechos não sustentarem a resposta, pedir esclarecimento ou oferecer atendente. Se embeddings estiverem indisponíveis, permitir busca textual e registrar a degradação. Testar a contingência local e o comportamento com base vazia; remover a FAQ fixa do fluxo habilitado após confirmar a paridade.
- Registrar metadados de diagnóstico: IDs e versões das fontes, latência e resultado. Evitar gravar tokens, dados de contato ou o conteúdo da conversa em logs.

## 3. Painel administrativo e melhoria contínua

- Criar área Base de Conhecimento com lista, edição, publicação, arquivamento, status da indexação, erro e nova tentativa.
- Implementar prévia sem disparar mensagem externa. Mostrar pergunta, resposta quando disponível, artigos/versões e trechos recuperados. Validar prévia de rascunho isolada da consulta em produção.
- Registrar lacunas quando faltar fonte ou houver encaminhamento por falta de conhecimento. Evitar copiar dados pessoais para a base. Permitir que a equipe sugira um rascunho e que um administrador revise e publique.

## 4. Avaliação e aceite

- Montar um conjunto fixo de perguntas diretas, paráfrases, termos exatos, perguntas de acompanhamento e casos sem cobertura. Anotar a fonte esperada de cada pergunta.
- Comparar busca textual, vetorial e híbrida com mesma base e perguntas. Medir fonte correta nas primeiras posições, sustentação da resposta, encaminhamento adequado e latência. Guardar dados e procedimento para reproduzir a demonstração.
- Testar permissões, publicação atômica, arquivamento, retry, falha do provedor, concorrência, histórico e callback. Usar bancos isolados e dados fictícios. Rodar a checagem do repositório e revisar com Prisma.
- Demonstrar a mesma pergunta antes e depois de publicar um artigo. Verificar que a fonte aparece na prévia e que a versão publicada entra em uso sem reiniciar o serviço.

## Ordem de entrega

1. Persistência, ciclo editorial e indexação isolada.
2. Busca híbrida, integração e testes do AutoGuia.
3. Painel editorial e prévia.
4. Lacunas, avaliação comparativa e demonstração.

Publicação no ambiente principal, uso de contatos reais, commit e push ficam fora desta execução até autorização específica.
