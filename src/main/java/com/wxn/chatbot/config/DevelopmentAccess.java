package com.wxn.chatbot.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class DevelopmentAccess {

    private final Environment environment;
    private final String key;
    private final long companyId;

    public DevelopmentAccess(
        Environment environment,
        @Value("${wxn.dev.api-key:}") String key,
        @Value("${wxn.dev.company-id:1}") long companyId
    ) {
        this.environment = environment;
        this.key = key;
        this.companyId = companyId;
    }

    public boolean enabled() {
        return environment.acceptsProfiles(Profiles.of("dev & !prod")) && key.length() >= 32 && companyId > 0;
    }

    public boolean validKey(String supplied) {
        return (
            enabled() &&
            supplied != null &&
            MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)
            )
        );
    }

    public boolean allows(long tenant) {
        return enabled() && companyId == tenant;
    }

    public long companyId() {
        return companyId;
    }
}
