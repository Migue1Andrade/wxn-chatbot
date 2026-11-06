package com.wxn.chatbot.auth.config;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.config.DevelopmentAccess;
import com.wxn.chatbot.user.entity.UserRole;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AccountAccessInterceptor implements HandlerInterceptor {

    private final AccountSessions sessions;
    private final DevelopmentAccess access;
    private final String origin;
    private final com.wxn.chatbot.auth.service.ConversationAuthorization conversations;

    public AccountAccessInterceptor(
        AccountSessions sessions,
        com.wxn.chatbot.auth.service.ConversationAuthorization conversations,
        DevelopmentAccess access,
        @Value("${wxn.dev.frontend-origin:http://localhost:4002}") String origin
    ) {
        this.sessions = sessions;
        this.conversations = conversations;
        this.access = access;
        this.origin = origin;
    }

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getServletPath();
        if ("OPTIONS".equals(request.getMethod()) || path.startsWith("/api/webhooks/")) {
            return true;
        }
        if (path.startsWith("/api/bootstrap/whatsapp/")) {
            if ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod())) {
                return true;
            }
            if (request.getHeader("Origin") != null && !origin.equals(request.getHeader("Origin"))) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Origem não permitida");
            }
            return true;
        }
        if (path.equals("/api/dev/users/seed") && "POST".equals(request.getMethod())) {
            String remote = request.getRemoteAddr();
            if (
                !access.enabled() ||
                !access.validKey(request.getHeader("X-Dev-Api-Key")) ||
                request.getHeader("Origin") != null ||
                !("127.0.0.1".equals(remote) || "::1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote))
            ) {
                throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "Seed permitido somente por execução local manual em dev"
                );
            }
            return true;
        }
        boolean write = !"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod());
        if (write) {
            if (request.getHeader("Origin") != null && !origin.equals(request.getHeader("Origin"))) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Origem não permitida");
            }
            sessions.csrf(request);
        }
        if (
            (path.equals("/api/auth/session") && "GET".equals(request.getMethod())) ||
            (path.equals("/api/auth/login") && "POST".equals(request.getMethod()))
        ) {
            return true;
        }
        var user = sessions.require(request.getSession(false));
        String[] segments = path.split("/");
        if (segments.length >= 4 && "conversations".equals(segments[2])) {
            conversations.require(segments[3], user);
        }
        if (segments.length >= 4 && "messages".equals(segments[2])) {
            conversations.message(segments[3], user);
        }
        boolean administration =
            path.startsWith("/api/admin/") ||
            path.matches("/api/conversations/[^/]+/(assign|transfer|assume)") ||
            path.matches("/api/whatsapp/channels/[^/]+/zapi/.*") ||
            path.startsWith("/api/users") ||
            path.startsWith("/api/departments") ||
            path.startsWith("/api/dev/") ||
            (path.startsWith("/api/members") && write) ||
            (path.startsWith("/api/whatsapp/channels") && write);
        if (administration && user.role() != UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Operação disponível somente para ADMIN");
        }
        return true;
    }
}
