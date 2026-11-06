package com.wxn.chatbot.whatsapp.zapi;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("legacy")
public class ZApiWebhookLimitFilter extends OncePerRequestFilter {

    private record Window(long minute, java.util.concurrent.atomic.AtomicInteger count) {}

    private final java.util.concurrent.ConcurrentMap<String, Window> windows =
        new java.util.concurrent.ConcurrentHashMap<>();

    private boolean permitted(String remote) {
        long minute = System.currentTimeMillis() / 60000;
        if (windows.size() >= 4096) {
            windows.entrySet().removeIf(entry -> entry.getValue().minute() < minute);
            if (windows.size() >= 4096 && !windows.containsKey(remote)) {
                return false;
            }
        }
        var window = windows.compute(remote, (key, old) ->
            old == null || old.minute() != minute
                ? new Window(minute, new java.util.concurrent.atomic.AtomicInteger())
                : old
        );
        return window.count().incrementAndGet() <= 6000;
    }

    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/webhooks/zapi/");
    }

    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws IOException, ServletException {
        if (!permitted(request.getRemoteAddr())) {
            response.setHeader("Retry-After", "60");
            response.sendError(429);
            return;
        }
        if (request.getContentLengthLong() > 262144) {
            response.sendError(413);
            return;
        }
        byte[] bytes = request.getInputStream().readNBytes(262145);
        if (bytes.length > 262144) {
            response.sendError(413);
            return;
        }
        chain.doFilter(
            new HttpServletRequestWrapper(request) {
                public ServletInputStream getInputStream() {
                    var input = new ByteArrayInputStream(bytes);
                    return new ServletInputStream() {
                        public int read() {
                            return input.read();
                        }

                        public boolean isFinished() {
                            return input.available() == 0;
                        }

                        public boolean isReady() {
                            return true;
                        }

                        public void setReadListener(ReadListener listener) {
                            throw new UnsupportedOperationException();
                        }
                    };
                }

                public BufferedReader getReader() {
                    return new BufferedReader(
                        new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8)
                    );
                }
            },
            response
        );
    }
}
