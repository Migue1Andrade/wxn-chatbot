package com.wxn.chatbot.websocket;

import java.security.Principal;

public record DevStompPrincipal(long companyId, String sessionId) implements Principal {
    @Override
    public String getName() {
        return "dev:" + companyId + ":" + sessionId;
    }
}
