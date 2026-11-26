package com.wxn.chatbot.openbot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openbot.support")
public record SupportProperties(
    boolean enabled,
    String webhookUrl,
    String serviceToken,
    String hmacSecret,
    Long autoAssignBotId,
    Long autoAssignChannelId
) {}
