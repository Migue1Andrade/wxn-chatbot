package com.wxn.chatbot.auth.config;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.message.repository.MessageRepository;
import jakarta.servlet.http.*;
import java.util.*;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Serialize REST writes against transfers before the access interceptor rechecks assignment. */
@Component
public class ConversationWriteGuard implements HandlerInterceptor {

    private final AccountSessions sessions;
    private final MessageRepository messages;
    private final com.wxn.chatbot.conversation.service.ConversationWriteCoordinator coordinator;

    public ConversationWriteGuard(
        AccountSessions sessions,
        MessageRepository messages,
        com.wxn.chatbot.conversation.service.ConversationWriteCoordinator coordinator
    ) {
        this.sessions = sessions;
        this.messages = messages;
        this.coordinator = coordinator;
    }

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
            return true;
        }
        String[] parts = request.getServletPath().split("/");
        if (parts.length < 4 || !Set.of("conversations", "messages").contains(parts[2])) {
            return true;
        }
        sessions.require(request.getSession(false));
        if (!ObjectId.isValid(parts[3])) {
            throw ApiException.badRequest("Identificador inválido");
        }
        String id = parts[3];
        if ("messages".equals(parts[2])) {
            id = messages
                .findById(new ObjectId(id))
                .orElseThrow(() -> ApiException.notFound("Mensagem não encontrada"))
                .getConversationId()
                .toHexString();
        }
        request.setAttribute("wxn.write.lease", coordinator.acquire(id));
        return true;
    }

    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception error
    ) {
        var lease =
            (com.wxn.chatbot.conversation.service.ConversationWriteCoordinator.Lease) request.getAttribute(
                "wxn.write.lease"
            );
        if (lease != null) {
            lease.close();
        }
    }
}
