package com.wxn.chatbot.whatsapp.evolution;

import com.wxn.chatbot.common.ApiException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EvolutionConfiguration {

    private final String apiUrl;
    private final String apiKey;
    private final String instanceName;
    private final String webhookUrl;
    private final String webhookSecret;
    private final String bootstrapToken;
    private final boolean allowHttp;

    public EvolutionConfiguration(
        @Value("${EVOLUTION_API_URL:http://localhost:8080}") String apiUrl,
        @Value("${EVOLUTION_API_KEY:}") String apiKey,
        @Value("${EVOLUTION_INSTANCE_NAME:wxn-local}") String instanceName,
        @Value("${EVOLUTION_WEBHOOK_URL:}") String webhookUrl,
        @Value("${EVOLUTION_WEBHOOK_SECRET:}") String webhookSecret,
        @Value("${WXN_BOOTSTRAP_TOKEN:}") String bootstrapToken,
        @Value("${EVOLUTION_ALLOW_HTTP:false}") boolean allowHttp
    ) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.instanceName = instanceName;
        this.webhookUrl = webhookUrl;
        this.webhookSecret = webhookSecret;
        this.bootstrapToken = bootstrapToken;
        this.allowHttp = allowHttp;
    }

    public void requireConfigured() {
        try {
            URI uri = URI.create(apiUrl);
            if (
                ("http".equals(uri.getScheme()) && !allowHttp && !"localhost".equals(uri.getHost())) ||
                ("https".equals(uri.getScheme()) && uri.getHost() == null) ||
                uri.getUserInfo() != null ||
                uri.getQuery() != null ||
                uri.getFragment() != null ||
                apiKey.isBlank() ||
                !instanceName.matches("[a-zA-Z0-9_-]{3,80}")
            ) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            throw ApiException.conflict("Configuração da Evolution API incompleta ou inválida");
        }
    }

    public boolean configured() {
        try {
            requireConfigured();
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    public String apiUrl() {
        requireConfigured();
        return apiUrl.replaceAll("/$", "");
    }

    public String apiKey() {
        requireConfigured();
        return apiKey;
    }

    public String instanceName() {
        requireConfigured();
        return instanceName;
    }

    public String webhookUrl() {
        boolean localHttp = allowHttp && webhookUrl.startsWith("http://");
        if (
            webhookUrl.isBlank() ||
            (!webhookUrl.startsWith("https://") && !localHttp) ||
            webhookUrl.endsWith("/")
        ) {
            throw ApiException.conflict("Configure EVOLUTION_WEBHOOK_URL como HTTPS ou URL interna local");
        }
        return webhookUrl;
    }

    public String webhookSecret() {
        if (webhookSecret.length() < 32 || !webhookSecret.matches("[a-zA-Z0-9_-]+")) {
            throw ApiException.conflict("Configure EVOLUTION_WEBHOOK_SECRET com pelo menos 32 caracteres");
        }
        return webhookSecret;
    }

    public boolean validBootstrapToken(String supplied) {
        if (bootstrapToken.length() < 32 || supplied == null || supplied.length() > 256) {
            return false;
        }
        return MessageDigest.isEqual(
            bootstrapToken.getBytes(StandardCharsets.UTF_8),
            supplied.getBytes(StandardCharsets.UTF_8)
        );
    }

    public boolean bootstrapConfigured() {
        return bootstrapToken.length() >= 32;
    }
}
