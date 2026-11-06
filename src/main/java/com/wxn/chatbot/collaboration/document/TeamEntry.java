package com.wxn.chatbot.collaboration.document;

import java.time.Instant;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("team_entries")
public record TeamEntry(
    @Id ObjectId id,
    ObjectId conversationId,
    Long authorUserId,
    String authorName,
    String kind,
    String content,
    boolean pinned,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt,
    long version,
    List<Revision> revisions,
    String operationId
) {
    public record Revision(String content, Long actorUserId, Instant at) {}
}
