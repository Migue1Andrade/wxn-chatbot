package com.wxn.chatbot.config;

import java.time.*;
import java.util.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.index.*;

@Configuration
public class MongoConfiguration {

    @WritingConverter
    public enum OffsetWriter implements Converter<OffsetDateTime, Date> {
        INSTANCE;

        public Date convert(OffsetDateTime value) {
            return Date.from(value.toInstant());
        }
    }

    @ReadingConverter
    public enum OffsetReader implements Converter<Date, OffsetDateTime> {
        INSTANCE;

        public OffsetDateTime convert(Date value) {
            return value.toInstant().atOffset(ZoneOffset.UTC);
        }
    }

    @Bean
    MongoCustomConversions mongoConversions() {
        return new MongoCustomConversions(List.of(OffsetWriter.INSTANCE, OffsetReader.INSTANCE));
    }

    @Bean
    ApplicationRunner canonicalIndexes(MongoTemplate mongo) {
        return args -> {
            for (String collection : List.of(
                "conversations",
                "messages",
                "message_logs",
                "conversation_events",
                "conversation_drafts",
                "conversation_reads",
                "team_entries"
            )) {
                mongo.indexOps(collection).createIndex(
                    new Index()
                        .on("seedKey", Sort.Direction.ASC)
                        .unique()
                        .named("demo_seed_key_unique")
                        .partial(
                            PartialIndexFilter.of(
                                new org.bson.Document("seedKey", new org.bson.Document("$type", "string"))
                            )
                        )
                );
            }
            mongo
                .indexOps("conversations")
                .createIndex(new Index().on("priority", Sort.Direction.ASC).on("status", Sort.Direction.ASC));
            mongo
                .indexOps("conversations")
                .createIndex(new Index().on("waitingForAgentSince", Sort.Direction.ASC));
            mongo
                .indexOps("team_entries")
                .createIndex(
                    new Index()
                        .on("conversationId", Sort.Direction.ASC)
                        .on("kind", Sort.Direction.ASC)
                        .on("pinned", Sort.Direction.DESC)
                        .on("createdAt", Sort.Direction.DESC)
                );
            mongo
                .indexOps("team_entries")
                .createIndex(
                    new Index()
                        .on("conversationId", Sort.Direction.ASC)
                        .on("authorUserId", Sort.Direction.ASC)
                        .on("operationId", Sort.Direction.ASC)
                        .unique()
                );
            mongo
                .indexOps("conversation_drafts")
                .createIndex(
                    new Index()
                        .on("conversationId", Sort.Direction.ASC)
                        .on("userId", Sort.Direction.ASC)
                        .on("mode", Sort.Direction.ASC)
                        .unique()
                );
            mongo
                .indexOps("conversations")
                .createIndex(
                    new Index()
                        .on("status", Sort.Direction.ASC)
                        .on("updatedAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.ASC)
                );
            mongo
                .indexOps("conversations")
                .createIndex(
                    new Index().on("assignedMemberId", Sort.Direction.ASC).on("status", Sort.Direction.ASC)
                );
            mongo
                .indexOps("conversations")
                .createIndex(
                    new Index()
                        .on("status", Sort.Direction.ASC)
                        .on("assignedMemberId", Sort.Direction.ASC)
                        .on("updatedAt", Sort.Direction.DESC)
                );
            mongo
                .indexOps("conversation_events")
                .createIndex(new Index().on("eventKey", Sort.Direction.ASC).unique());
            mongo
                .indexOps("conversation_events")
                .createIndex(
                    new Index()
                        .on("conversationId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.ASC)
                );
            mongo
                .indexOps("conversation_reads")
                .createIndex(
                    new Index()
                        .on("conversationId", Sort.Direction.ASC)
                        .on("memberId", Sort.Direction.ASC)
                        .unique()
                );
            mongo
                .indexOps("messages")
                .createIndex(
                    new Index()
                        .on("conversationId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC)
                );
            mongo
                .indexOps("messages")
                .createIndex(
                    new Index()
                        .on("conversationId", Sort.Direction.ASC)
                        .on("direction", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.ASC)
                        .on("_id", Sort.Direction.ASC)
                );
            mongo
                .indexOps("message_logs")
                .createIndex(
                    new Index()
                        .on("eventKey", Sort.Direction.ASC)
                        .unique()
                        .named("message_log_event_key_unique")
                );
            mongo
                .indexOps("conversations")
                .createIndex(
                    new Index()
                        .on("channelId", Sort.Direction.ASC)
                        .on("conversationKey", Sort.Direction.ASC)
                        .unique()
                        .named("conversation_channel_key_unique")
                );
            mongo.indexOps("messages").createIndex(
                new Index()
                    .on("channelId", Sort.Direction.ASC)
                    .on("operationId", Sort.Direction.ASC)
                    .unique()
                    .named("message_channel_operation_unique")
                    .partial(
                        PartialIndexFilter.of(
                            new org.bson.Document("operationId", new org.bson.Document("$type", "string"))
                        )
                    )
            );
            mongo
                .indexOps("messages")
                .createIndex(
                    new Index()
                        .on("channelId", Sort.Direction.ASC)
                        .on("providerTrackingId", Sort.Direction.ASC)
                );
            // Outbound messages have no external ID until the provider responds.
            mongo.indexOps("messages").createIndex(
                new Index()
                    .on("channelId", Sort.Direction.ASC)
                    .on("externalMessageId", Sort.Direction.ASC)
                    .unique()
                    .named("message_channel_external_unique")
                    .partial(
                        PartialIndexFilter.of(
                            new org.bson.Document(
                                "externalMessageId",
                                new org.bson.Document("$type", "string")
                            )
                        )
                    )
            );
            mongo
                .indexOps("messages")
                .createIndex(
                    new Index().on("conversationId", Sort.Direction.ASC).on("createdAt", Sort.Direction.ASC)
                );
        };
    }
}
