package com.wxn.chatbot.autoguia.embedding;

import com.wxn.chatbot.autoguia.domain.EmbeddingVector;

public interface KnowledgeEmbeddingProvider {
    EmbeddingVector embed(String text);
}
