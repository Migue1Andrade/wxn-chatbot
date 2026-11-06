package com.wxn.chatbot.kanban.service;

import com.wxn.chatbot.conversation.document.Conversation;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AttentionScoreService {

    public record Attention(int score, List<String> reasons) {}

    public Attention evaluate(Conversation c) {
        if (Set.of("CLOSED", "RESOLVED").contains(c.operationalStatus().name())) {
            return new Attention(0, List.of());
        }
        int score = 0;
        List<String> reasons = new ArrayList<>();
        if ("URGENT".equals(c.getPriority())) {
            score += 45;
            reasons.add("Prioridade urgente");
        } else if ("HIGH".equals(c.getPriority())) {
            score += 25;
            reasons.add("Prioridade alta");
        }
        if (c.getAssignedMemberId() == null) {
            score += 35;
            reasons.add("Sem responsável");
        }
        if (c.getWaitingForAgentSince() != null) {
            reasons.add("Aguardando resposta da equipe");
        }
        return new Attention(score, List.copyOf(reasons));
    }
}
