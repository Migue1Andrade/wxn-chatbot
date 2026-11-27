package com.wxn.chatbot.autoguia.generation;

import com.wxn.chatbot.autoguia.repository.KnowledgeRetrievalRepository.SourceChunk;
import java.util.List;

public interface KnowledgeAnswerGenerator {
    default String selectEvidence(String question, List<SourceChunk> sources) {
        return "";
    }

    String generate(String question, List<SourceChunk> sources);
}
