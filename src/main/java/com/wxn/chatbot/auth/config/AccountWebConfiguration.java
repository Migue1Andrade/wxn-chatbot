package com.wxn.chatbot.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class AccountWebConfiguration implements WebMvcConfigurer {

    private final AccountAccessInterceptor access;
    private final ConversationWriteGuard writes;

    public AccountWebConfiguration(AccountAccessInterceptor access, ConversationWriteGuard writes) {
        this.access = access;
        this.writes = writes;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(writes).addPathPatterns("/api/**");
        registry.addInterceptor(access).addPathPatterns("/api/**");
    }
}
