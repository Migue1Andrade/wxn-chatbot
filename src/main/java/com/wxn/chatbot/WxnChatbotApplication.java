package com.wxn.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableAsync
@org.springframework.boot.context.properties.EnableConfigurationProperties(
    com.wxn.chatbot.openbot.SupportProperties.class
)
public class WxnChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(WxnChatbotApplication.class, args);
    }
}
