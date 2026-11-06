package com.wxn.chatbot.whatsapp.zapi;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.ProviderType;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Environment-backed secure resolution for the single configured local channel. */
@Component
@Profile("legacy")
public class ZApiConfiguration {

    private final String baseUrl;
    private final String clientToken;
    private final Long channelId;
    private final String webhookSecret;
    private final String publicBaseUrl;

    @Value("${ZAPI_WEBHOOK_SECRET_PREVIOUS:}")
    private String previousSecret = "";

    public boolean authenticated(String supplied) {
        if (supplied == null || supplied.length() > 256) {
            return false;
        }
        var bytes = supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        boolean current = java.security.MessageDigest.isEqual(
            webhookSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8),
            bytes
        );
        boolean previous = java.security.MessageDigest.isEqual(
            previousSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            bytes
        );
        return current | (previousSecret.length() >= 32 && previous);
    }

    public ZApiConfiguration(
        @Value("${ZAPI_BASE_URL:}") String baseUrl,
        @Value("${ZAPI_CLIENT_TOKEN:}") String clientToken,
        @Value("${ZAPI_CHANNEL_ID:0}") Long channelId,
        @Value("${ZAPI_WEBHOOK_SECRET:}") String webhookSecret,
        @Value("${ZAPI_WEBHOOK_PUBLIC_BASE_URL:${ZAPI_PUBLIC_BASE_URL:}}") String publicBaseUrl
    ) {
        this.baseUrl = baseUrl;
        this.clientToken = clientToken;
        this.channelId = channelId;
        this.webhookSecret = webhookSecret;
        this.publicBaseUrl = publicBaseUrl;
    }

    public void requireChannel(WhatsAppChannel channel) {
        requireConfigured();
        if (
            !channelId.equals(channel.getId()) ||
            channel.getProvider() != ProviderType.ZAPI ||
            !instanceId().equals(channel.getExternalChannelId()) ||
            channel.getPhoneNumber() == null
        ) {
            throw ApiException.conflict("Canal não corresponde à configuração segura Z-API");
        }
    }

    public void requireConfigured() {
        try {
            URI uri = URI.create(baseUrl);
            if (
                !"https".equals(uri.getScheme()) ||
                !"api.z-api.io".equals(uri.getHost()) ||
                uri.getPort() != -1 ||
                uri.getUserInfo() != null ||
                uri.getQuery() != null ||
                uri.getFragment() != null ||
                !uri.getRawPath().matches("/instances/[a-zA-Z0-9_-]+/token/[a-zA-Z0-9_-]+") ||
                clientToken.isBlank() ||
                channelId < 1
            ) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            throw ApiException.conflict("Configuração segura Z-API incompleta ou inválida");
        }
    }

    public String baseUrl() {
        requireConfigured();
        return baseUrl;
    }

    public String clientToken() {
        requireConfigured();
        return clientToken;
    }

    public String instanceId() {
        requireConfigured();
        return URI.create(baseUrl).getPath().split("/")[2];
    }

    public Long channelId() {
        return channelId;
    }

    public String webhookSecret() {
        if (webhookSecret.length() < 32 || !webhookSecret.matches("[a-zA-Z0-9_-]+")) {
            throw ApiException.conflict("Configure ZAPI_WEBHOOK_SECRET com pelo menos 32 caracteres seguros");
        }
        return webhookSecret;
    }

    public String callback(String operation) {
        URI uri;
        try {
            uri = URI.create(publicBaseUrl);
        } catch (IllegalArgumentException error) {
            throw ApiException.conflict("URL pública de webhook inválida");
        }
        if (
            !"https".equals(uri.getScheme()) ||
            uri.getHost() == null ||
            uri.getUserInfo() != null ||
            uri.getQuery() != null ||
            uri.getFragment() != null ||
            publicBaseUrl.endsWith("/")
        ) {
            throw ApiException.conflict("ZAPI_PUBLIC_BASE_URL deve ser HTTPS sem barra final");
        }
        return publicBaseUrl + "/api/webhooks/zapi/" + operation + "?secret=" + webhookSecret();
    }
}
