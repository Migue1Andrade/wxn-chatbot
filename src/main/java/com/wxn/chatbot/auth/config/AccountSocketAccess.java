package com.wxn.chatbot.auth.config;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.common.ApiException;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;

@Component
public class AccountSocketAccess implements WebSocketHandlerDecoratorFactory, ChannelInterceptor {

    private final AccountSessions accounts;
    private final com.wxn.chatbot.auth.service.ConversationAuthorization authorization;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final ConcurrentHashMap<String, WebSocketSession> connections = new ConcurrentHashMap<>();

    public AccountSocketAccess(
        AccountSessions accounts,
        com.wxn.chatbot.auth.service.ConversationAuthorization authorization,
        com.fasterxml.jackson.databind.ObjectMapper json
    ) {
        this.authorization = authorization;
        this.json = json;
        this.accounts = accounts;
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                connections.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                connections.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        String id = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        WebSocketSession socket = id == null ? null : connections.get(id);
        if (socket == null) {
            return id == null ? message : null;
        }
        try {
            var actor = accounts.require(
                (HttpSession) socket.getAttributes().get(AccountSessions.SOCKET_SESSION)
            );
            String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
            boolean notification = ("/topic/users/" + actor.id() + "/notifications").equals(destination);
            if (
                !notification &&
                destination != null &&
                actor.role() != com.wxn.chatbot.user.entity.UserRole.ADMIN &&
                !("/topic/members/" + actor.memberId() + "/conversations").equals(destination)
            ) {
                return null;
            }
            if (
                !notification &&
                destination != null &&
                actor.role() != com.wxn.chatbot.user.entity.UserRole.ADMIN
            ) {
                try {
                    byte[] payload =
                        message.getPayload() instanceof byte[] bytes
                            ? bytes
                            : message
                                  .getPayload()
                                  .toString()
                                  .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    var event = json.readTree(payload);
                    if (!"conversation.access.revoked".equals(event.path("type").asText())) {
                        authorization.require(event.path("conversationId").asText(), actor);
                    }
                } catch (IOException | ApiException denied) {
                    return null;
                }
            }
            return message;
        } catch (ApiException | IllegalStateException expired) {
            connections.remove(id);
            try {
                socket.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException closed) {
                /* Connection already unavailable. */
            }
            return null;
        }
    }
}
