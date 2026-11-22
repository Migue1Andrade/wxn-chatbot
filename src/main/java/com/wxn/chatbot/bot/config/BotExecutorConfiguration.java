package com.wxn.chatbot.bot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class BotExecutorConfiguration {

    public static final String BOT_EXECUTOR = "botExecutor";

    // Pool próprio e limitado: a chamada ao bot leva segundos e não pode ocupar a thread do webhook.
    @Bean(name = BOT_EXECUTOR)
    public ThreadPoolTaskExecutor botExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("bot-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
