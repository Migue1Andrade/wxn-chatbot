package com.wxn.chatbot.collaboration.repository;

import com.wxn.chatbot.collaboration.document.TeamEntry;
import com.wxn.chatbot.common.ApiException;
import java.time.Instant;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;

@Repository
public class TeamMutationRepository {

    private final MongoTemplate mongo;

    public TeamMutationRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public TeamEntry update(
        TeamEntry entry,
        long expected,
        String content,
        boolean pinned,
        boolean delete,
        Long actor
    ) {
        var now = Instant.now();
        var update = new Update()
            .set("content", content)
            .set("pinned", pinned)
            .set("updatedAt", now)
            .inc("version", 1)
            .push("revisions", new TeamEntry.Revision(entry.content(), actor, now));
        if (delete) {
            update.set("deletedAt", now);
        }
        var result = mongo.findAndModify(
            Query.query(
                Criteria.where("_id").is(entry.id()).and("version").is(expected).and("deletedAt").is(null)
            ),
            update,
            FindAndModifyOptions.options().returnNew(true),
            TeamEntry.class
        );
        if (result == null) {
            throw ApiException.conflict("Registro alterado; atualize antes de repetir");
        }
        return result;
    }

    public String draft(org.bson.types.ObjectId conversation, Long user, String mode) {
        var row = mongo.findOne(
            draftQuery(conversation, user, mode),
            org.bson.Document.class,
            "conversation_drafts"
        );
        return row == null ? "" : row.getString("content");
    }

    public void draft(org.bson.types.ObjectId conversation, Long user, String mode, String content) {
        mongo.upsert(
            draftQuery(conversation, user, mode),
            new Update().set("content", content == null ? "" : content).set("updatedAt", Instant.now()),
            "conversation_drafts"
        );
    }

    private Query draftQuery(org.bson.types.ObjectId conversation, Long user, String mode) {
        return Query.query(
            Criteria.where("conversationId").is(conversation).and("userId").is(user).and("mode").is(mode)
        );
    }
}
