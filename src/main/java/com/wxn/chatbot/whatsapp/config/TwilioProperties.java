package com.wxn.chatbot.whatsapp.config;

import com.wxn.chatbot.common.ApiException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("legacy")
@ConfigurationProperties(prefix = "wxn.twilio")
public class TwilioProperties {

    private Long channelId;
    private String channelKey;
    private String accountSid;
    private String authToken;
    private String phoneNumber;
    private String publicBaseUrl;

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long value) {
        channelId = value;
    }

    public String getChannelKey() {
        return channelKey;
    }

    public void setChannelKey(String value) {
        channelKey = value;
    }

    public String getAccountSid() {
        return accountSid;
    }

    public void setAccountSid(String value) {
        accountSid = value;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String value) {
        authToken = value;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String value) {
        phoneNumber = value;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String value) {
        publicBaseUrl = value;
    }

    public void requireConfigured() {
        if (
            channelId == null ||
            channelId < 1 ||
            blank(channelKey) ||
            !channelKey.matches("[a-zA-Z0-9_-]{8,100}") ||
            blank(accountSid) ||
            blank(authToken) ||
            blank(phoneNumber) ||
            blank(publicBaseUrl) ||
            !publicBaseUrl.startsWith("https://") ||
            publicBaseUrl.endsWith("/") ||
            publicBaseUrl.contains("?") ||
            publicBaseUrl.contains("#")
        ) {
            throw ApiException.conflict("Configuração segura Twilio incompleta");
        }
    }

    public String inboundUrl() {
        return publicBaseUrl + "/api/webhooks/whatsapp/twilio/" + channelKey;
    }

    public String statusUrl() {
        return inboundUrl() + "/status";
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
