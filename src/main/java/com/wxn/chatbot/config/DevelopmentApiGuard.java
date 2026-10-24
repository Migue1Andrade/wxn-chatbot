package com.wxn.chatbot.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DevelopmentApiGuard extends OncePerRequestFilter {

    private static final Pattern TENANT_PATH = Pattern.compile("^/api/companies/([0-9]+)(?:/.*)?$");
    private final DevelopmentAccess access;

    public DevelopmentApiGuard(DevelopmentAccess access) {
        this.access = access;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !(
            path.equals("/api/companies") ||
            path.startsWith("/api/companies/") ||
            path.startsWith("/api/dev/")
        );
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
        var tenant = TENANT_PATH.matcher(request.getServletPath());
        if (tenant.matches()) {
            try {
                if (!access.allows(Long.parseLong(tenant.group(1)))) {
                    response.sendError(403);
                    return;
                }
            } catch (NumberFormatException invalid) {
                response.sendError(400);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
