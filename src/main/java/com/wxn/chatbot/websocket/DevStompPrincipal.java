package com.wxn.chatbot.websocket;

import java.security.Principal;

public record DevStompPrincipal(String sessionId) implements Principal {
    @Override
    public String getName() {
        return "dev:" + sessionId;
    }
}
