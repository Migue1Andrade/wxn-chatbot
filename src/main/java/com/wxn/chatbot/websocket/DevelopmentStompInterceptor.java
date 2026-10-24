package com.wxn.chatbot.websocket;

import com.wxn.chatbot.config.DevelopmentAccess;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.stereotype.Component;

@Component
public class DevelopmentStompInterceptor implements ChannelInterceptor {

    private final DevelopmentAccess access;

    public DevelopmentStompInterceptor(DevelopmentAccess access) {
        this.access = access;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null) return message;
        if (headers.getCommand() == StompCommand.CONNECT || headers.getCommand() == StompCommand.STOMP) {
            String suppliedKey = headers.getFirstNativeHeader("X-Dev-Api-Key");
            headers.removeNativeHeader("X-Dev-Api-Key");
            long tenant;
            try {
                tenant = Long.parseLong(headers.getFirstNativeHeader("companyId"));
            } catch (RuntimeException invalid) {
                throw new MessageDeliveryException("Contexto de desenvolvimento inválido");
            }
            if (!access.validKey(suppliedKey) || !access.allows(tenant)) {
                throw new MessageDeliveryException("Acesso de desenvolvimento inválido");
            }
            headers.setUser(new DevStompPrincipal(tenant, headers.getSessionId()));
        } else if (headers.getCommand() == StompCommand.SUBSCRIBE) {
            if (
                !(headers.getUser() instanceof DevStompPrincipal principal) ||
                !access.allows(principal.companyId()) ||
                !StompDestinations.permitted(headers.getDestination(), principal.companyId())
            ) {
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
