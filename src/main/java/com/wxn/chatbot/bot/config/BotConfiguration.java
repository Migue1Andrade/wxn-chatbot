package com.wxn.chatbot.bot.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BotConfiguration {

    private static final int MIN_API_KEY_LENGTH = 32;

    private final boolean enabled;
    private final String serviceUrl;
    private final String apiKey;
    private final Long memberId;
    private final Duration timeout;

    public BotConfiguration(
        @Value("${BOT_ENABLED:false}") boolean enabled,
        @Value("${BOT_SERVICE_URL:http://localhost:4005}") String serviceUrl,
        @Value("${BOT_API_KEY:}") String apiKey,
        @Value("${BOT_MEMBER_ID:}") Long memberId,
        @Value("${BOT_TIMEOUT_SECONDS:45}") long timeoutSeconds
    ) {
        this.enabled = enabled;
        this.serviceUrl = serviceUrl;
        this.apiKey = apiKey;
        this.memberId = memberId;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        if (enabled) {
            requireValid();
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public URI replyUri() {
        return URI.create(serviceUrl + "/reply");
    }

    public String apiKey() {
        return apiKey;
    }

    public Long memberId() {
        return memberId;
    }

    public Duration timeout() {
        return timeout;
    }

    // Bot ligado com configuração incompleta impede a inicialização em vez de falhar a cada mensagem.
    private void requireValid() {
        URI uri;
        try {
            uri = URI.create(serviceUrl);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("BOT_SERVICE_URL inválida", error);
        }
        if (uri.getScheme() == null || uri.getHost() == null || serviceUrl.endsWith("/")) {
            throw new IllegalStateException("BOT_SERVICE_URL deve ser uma URL absoluta sem barra final");
        }
        if (apiKey == null || apiKey.length() < MIN_API_KEY_LENGTH) {
            throw new IllegalStateException("BOT_API_KEY deve ter pelo menos 32 caracteres");
        }
        if (memberId == null || memberId < 1) {
            throw new IllegalStateException("BOT_MEMBER_ID obrigatório com o bot ligado");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalStateException("BOT_TIMEOUT_SECONDS deve ser positivo");
        }
    }
}
