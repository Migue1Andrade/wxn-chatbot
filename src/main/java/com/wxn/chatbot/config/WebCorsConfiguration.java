package com.wxn.chatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebCorsConfiguration implements WebMvcConfigurer {

    private final String origin;

    public WebCorsConfiguration(@Value("${wxn.dev.frontend-origin:http://localhost:4002}") String origin) {
        this.origin = origin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/api/**")
            .allowedOrigins(origin)
            .allowedMethods("GET", "POST", "PATCH", "OPTIONS")
            .allowedHeaders("Content-Type", "X-Dev-Api-Key")
            .maxAge(3600);
    }
}
