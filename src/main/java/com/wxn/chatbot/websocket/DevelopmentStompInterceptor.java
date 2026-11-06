package com.wxn.chatbot.websocket;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.config.DevelopmentAccess;
import jakarta.servlet.http.HttpSession;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.stereotype.Component;

@Component
public class DevelopmentStompInterceptor implements ChannelInterceptor {

    private final DevelopmentAccess access;
    private final AccountSessions sessions;

    public DevelopmentStompInterceptor(DevelopmentAccess access, AccountSessions sessions) {
        this.access = access;
        this.sessions = sessions;
    }

    private boolean permitted(StompHeaderAccessor headers) {
        var httpSession = (HttpSession) headers.getSessionAttributes().get(AccountSessions.SOCKET_SESSION);
        var user = sessions.require(httpSession);
        if (("/topic/users/" + user.id() + "/notifications").equals(headers.getDestination())) {
            return true;
        }
        return user.role() == com.wxn.chatbot.user.entity.UserRole.ADMIN
            ? StompDestinations.conversations().equals(headers.getDestination()) ||
                  "/topic/admin/channels".equals(headers.getDestination())
            : user.memberId() != null &&
                  ("/topic/members/" + user.memberId() + "/conversations").equals(headers.getDestination());
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null) {
            return message;
        }
        if (headers.getCommand() != StompCommand.DISCONNECT) {
            Object session =
                headers.getSessionAttributes() == null
                    ? null
                    : headers.getSessionAttributes().get(AccountSessions.SOCKET_SESSION);
            if (!(session instanceof HttpSession httpSession)) {
                throw new MessageDeliveryException("Sessão de usuário obrigatória");
            }
            try {
                sessions.require(httpSession);
            } catch (com.wxn.chatbot.common.ApiException denied) {
                throw new MessageDeliveryException("Sessão encerrada ou conta indisponível");
            }
        }
        if (headers.getCommand() == StompCommand.CONNECT || headers.getCommand() == StompCommand.STOMP) {
            String suppliedKey = headers.getFirstNativeHeader("X-Dev-Api-Key");
            headers.removeNativeHeader("X-Dev-Api-Key");
            if (!access.validKey(suppliedKey)) {
                throw new MessageDeliveryException("Acesso de desenvolvimento inválido");
            }
            headers.setUser(new DevStompPrincipal(headers.getSessionId()));
        } else if (headers.getCommand() == StompCommand.SUBSCRIBE) {
            if (!(headers.getUser() instanceof DevStompPrincipal) || !permitted(headers)) {
                throw new MessageDeliveryException("Assinatura de destino não permitida");
            }
        } else if (headers.getCommand() == StompCommand.SEND) {
            throw new MessageDeliveryException("Envio de mensagens permitido somente via REST");
        } else if (
            headers.getCommand() != StompCommand.DISCONNECT &&
            !(headers.getUser() instanceof DevStompPrincipal)
        ) {
            throw new MessageDeliveryException("Conexão não autenticada");
        }
        return message;
    }
}
