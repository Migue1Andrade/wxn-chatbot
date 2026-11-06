package com.wxn.chatbot.whatsapp.repository;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;

@Repository
public class WebhookInboxRepository {

    private final MongoTemplate mongo;

    public WebhookInboxRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public String accept(Long channel, String kind, JsonNode body) {
        String key = channel + ":" + kind + ":" + digest(canonical(body));
        Date now = new Date();
        mongo.upsert(
            Query.query(Criteria.where("_id").is(key)),
            new Update()
                .setOnInsert("channelId", channel)
                .setOnInsert("kind", kind)
                .setOnInsert("externalIdentity", body.path("senderLid").asText(null))
                .setOnInsert("payload", body.toString())
                .setOnInsert("state", "PENDING")
                .setOnInsert("attempts", 0)
                .setOnInsert("createdAt", now)
                .setOnInsert("nextAttemptAt", now)
                .setOnInsert("expiresAt", Date.from(Instant.now().plus(Duration.ofDays(7)))),
            "webhook_inbox"
        );
        return key;
    }

    public Document claim(String key) {
        Date now = new Date();
        Criteria ready = new Criteria().orOperator(
            new Criteria().andOperator(
                Criteria.where("state").in("PENDING", "FAILED"),
                Criteria.where("nextAttemptAt").lte(now)
            ),
            new Criteria().andOperator(
                Criteria.where("state").is("PROCESSING"),
                Criteria.where("updatedAt").lt(Date.from(Instant.now().minusSeconds(120)))
            )
        );
        return mongo.findAndModify(
            Query.query(Criteria.where("_id").is(key).and("attempts").lt(5)).addCriteria(ready),
            new Update().set("state", "PROCESSING").set("updatedAt", now).inc("attempts", 1),
            FindAndModifyOptions.options().returnNew(true),
            Document.class,
            "webhook_inbox"
        );
    }

    public void finish(String key, String outcome) {
        mongo.updateFirst(
            Query.query(Criteria.where("_id").is(key)),
            new Update()
                .set("state", "PROCESSED")
                .set("outcome", outcome)
                .set("updatedAt", new Date())
                .unset("payload")
                .unset("lastError"),
            "webhook_inbox"
        );
    }

    public void failed(String key) {
        mongo.updateFirst(
            Query.query(Criteria.where("_id").is(key)),
            new Update()
                .set("state", "FAILED")
                .set("lastError", "PROCESSING_FAILED_OR_CORRELATION_PENDING")
                .set("updatedAt", new Date())
                .set("nextAttemptAt", Date.from(Instant.now().plusSeconds(30))),
            "webhook_inbox"
        );
    }

    public List<Document> due() {
        return mongo.find(
            Query.query(Criteria.where("state").ne("PROCESSED").and("attempts").lt(5))
                .with(org.springframework.data.domain.Sort.by("createdAt"))
                .limit(25),
            Document.class,
            "webhook_inbox"
        );
    }

    public List<Document> failures(Long channel) {
        var query = Query.query(Criteria.where("channelId").is(channel).and("state").ne("PROCESSED"));
        query.fields().exclude("payload").exclude("externalIdentity");
        return mongo.find(
            query
                .with(
                    org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC,
                        "createdAt"
                    )
                )
                .limit(50),
            Document.class,
            "webhook_inbox"
        );
    }

    public void retry(Long channel, String key) {
        mongo.updateFirst(
            Query.query(Criteria.where("_id").is(key).and("channelId").is(channel).and("state").is("FAILED")),
            new Update().set("attempts", 0).set("nextAttemptAt", new Date()),
            "webhook_inbox"
        );
    }

    public static String digest(String text) {
        try {
            return HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(
                    text.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                )
            );
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String canonical(JsonNode value) {
        if (value.isObject()) {
            var fields = new TreeMap<String, String>();
            value.fields().forEachRemaining(entry -> fields.put(entry.getKey(), canonical(entry.getValue())));
            return fields
                .entrySet()
                .stream()
                .map(
                    entry ->
                        new com.fasterxml.jackson.databind.node.TextNode(entry.getKey()).toString() +
                        ":" +
                        entry.getValue()
                )
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value.isArray()) {
            var items = new ArrayList<String>();
            value.forEach(item -> items.add(canonical(item)));
            return items.stream().collect(java.util.stream.Collectors.joining(",", "[", "]"));
        }
        return value.toString();
    }
}
