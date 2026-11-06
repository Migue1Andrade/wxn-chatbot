package com.wxn.chatbot.demo.repository;

import java.util.*;
import java.util.function.Supplier;
import org.bson.Document;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
@Profile("dev & !prod")
public class DemoSeedStore {

    public record Result<T>(T value, boolean created) {}

    private final JdbcTemplate jdbc;
    private final MongoTemplate mongo;
    private final TransactionTemplate transactions;

    public DemoSeedStore(
        JdbcTemplate jdbc,
        MongoTemplate mongo,
        org.springframework.transaction.PlatformTransactionManager manager
    ) {
        this.jdbc = jdbc;
        this.mongo = mongo;
        this.transactions = new TransactionTemplate(manager);
    }

    public void preflight() {
        jdbc.queryForObject("select 1", Integer.class);
        mongo.executeCommand(new Document("ping", 1));
        jdbc.queryForObject("select count(*) from demo_seed_manifest", Long.class);
    }

    public Result<String> relational(String key, String kind, Supplier<String> create) {
        return transactions.execute(status -> {
            var ids = jdbc.queryForList(
                "select resource_id from demo_seed_manifest where seed_key = ?",
                String.class,
                key
            );
            if (!ids.isEmpty()) {
                return new Result<>(ids.get(0), false);
            }
            if (
                !java.util.Set.of(
                    "users",
                    "departments",
                    "whatsapp_channels",
                    "quick_replies",
                    "work_tasks",
                    "user_notifications",
                    "kanban_views"
                ).contains(kind)
            ) {
                throw new IllegalArgumentException("Tipo de seed inválido");
            }
            String id = create.get();
            jdbc.update(
                "insert into demo_seed_manifest(seed_key, resource_type, resource_id) values (?, ?, ?)",
                key,
                kind,
                id
            );
            return new Result<>(id, true);
        });
    }

    public void reference(String key, String kind, String id) {
        transactions.executeWithoutResult(status ->
            jdbc.update(
                "insert into demo_seed_manifest(seed_key,resource_type,resource_id) values (?, ?, ?) on conflict(seed_key) do nothing",
                key,
                kind,
                id
            )
        );
    }

    public <T> Result<T> document(String key, String collection, Class<T> type, Supplier<T> create) {
        var records = mongo.getCollection(collection);
        var query = new Document("seedKey", key);
        Document existing = records.find(query).first();
        if (existing != null) {
            return new Result<>(mongo.getConverter().read(type, existing), false);
        }
        Document row = new Document();
        mongo.getConverter().write(create.get(), row);
        row.put("seedKey", key);
        try {
            records.insertOne(row);
        } catch (com.mongodb.MongoWriteException duplicate) {
            if (duplicate.getError().getCode() != 11000) {
                throw duplicate;
            }
            existing = records.find(query).first();
            if (
                existing == null &&
                ("conversation_reads".equals(collection) || "conversation_drafts".equals(collection))
            ) {
                Document natural = new Document("conversationId", row.get("conversationId"));
                if ("conversation_reads".equals(collection)) {
                    natural.append("memberId", row.get("memberId"));
                } else {
                    natural.append("userId", row.get("userId")).append("mode", row.get("mode"));
                }
                existing = records.find(natural).first();
            }
            if (existing == null) {
                throw duplicate;
            }
            return new Result<>(mongo.getConverter().read(type, existing), false);
        }
        return new Result<>(mongo.getConverter().read(type, row), true);
    }

    public void linkLastMessage(String seedKey, org.bson.types.ObjectId messageId) {
        mongo
            .getCollection("conversations")
            .updateOne(
                new Document("seedKey", seedKey).append("lastMessageId", null).append("version", 0),
                new Document("$set", new Document("lastMessageId", messageId))
            );
    }

    public Map<String, Long> postgresCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : List.of(
            "users",
            "members",
            "departments",
            "member_departments",
            "whatsapp_channels",
            "quick_replies",
            "kanban_views",
            "sla_policy",
            "work_tasks",
            "user_notifications",
            "demo_seed_manifest"
        )) {
            counts.put(table, jdbc.queryForObject("select count(*) from " + table, Long.class));
        }
        return counts;
    }

    public Map<String, Long> mongoCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String collection : List.of(
            "conversations",
            "messages",
            "message_logs",
            "conversation_events",
            "conversation_reads",
            "conversation_drafts",
            "team_entries"
        )) {
            counts.put(collection, mongo.getCollection(collection).countDocuments());
        }
        return counts;
    }
}
