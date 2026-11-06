package com.wxn.chatbot.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DevelopmentApiGuard extends OncePerRequestFilter {

    private final DevelopmentAccess access;

    public DevelopmentApiGuard(DevelopmentAccess access) {
        this.access = access;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !(path.startsWith("/api/") && !path.startsWith("/api/webhooks/"));
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        if (!access.enabled()) {
            response.sendError(404);
            return;
        }
        // CORS validates preflight origin and requested headers in the MVC configuration.
        if ("OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        if (!access.validKey(request.getHeader("X-Dev-Api-Key"))) {
            response.sendError(401);
            return;
        }
        chain.doFilter(request, response);
    }
}
