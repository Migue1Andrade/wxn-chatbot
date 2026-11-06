package com.wxn.chatbot.whatsapp.config;

import java.time.Duration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@Configuration
public class WhatsAppIndexes {

    @Bean
    ApplicationRunner messagingIndexes(MongoTemplate mongo) {
        return args -> {
            mongo
                .indexOps("messages")
                .ensureIndex(
                    new Index().on("status", Sort.Direction.ASC).on("lastReconciledAt", Sort.Direction.ASC)
                );
            mongo
                .indexOps("contacts")
                .ensureIndex(
                    new Index().on("channelId", Sort.Direction.ASC).on("phone", Sort.Direction.ASC).unique()
                );
            mongo
                .indexOps("webhook_inbox")
                .ensureIndex(
                    new Index().on("state", Sort.Direction.ASC).on("nextAttemptAt", Sort.Direction.ASC)
                );
            mongo
                .indexOps("webhook_inbox")
                .ensureIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO));
            mongo
                .indexOps("webhook_inbox")
                .ensureIndex(
                    new Index().on("channelId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC)
                );
            mongo
                .indexOps("channel_operations")
                .ensureIndex(
                    new Index()
                        .on("channelId", Sort.Direction.ASC)
                        .on("operationId", Sort.Direction.ASC)
                        .unique()
                );
            mongo
                .indexOps("message_operations")
                .ensureIndex(
                    new Index()
                        .on("channelId", Sort.Direction.ASC)
                        .on("operationId", Sort.Direction.ASC)
                        .unique()
                );
        };
    }
}
