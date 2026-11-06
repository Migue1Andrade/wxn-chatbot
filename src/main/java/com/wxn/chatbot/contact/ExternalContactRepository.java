package com.wxn.chatbot.contact;

import java.util.Date;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;

/** Atomic identity registry; channel + normalized phone is deliberately not a global person merge. */
@Repository
public class ExternalContactRepository {

    private final MongoTemplate mongo;

    public ExternalContactRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public ObjectId resolve(Long channelId, String phone) {
        var query = Query.query(Criteria.where("channelId").is(channelId).and("phone").is(phone));
        try {
            return mongo
                .findAndModify(
                    query,
                    new Update()
                        .setOnInsert("channelId", channelId)
                        .setOnInsert("phone", phone)
                        .setOnInsert("createdAt", new Date()),
                    FindAndModifyOptions.options().upsert(true).returnNew(true),
                    Document.class,
                    "contacts"
                )
                .getObjectId("_id");
        } catch (org.springframework.dao.DuplicateKeyException race) {
            return mongo.findOne(query, Document.class, "contacts").getObjectId("_id");
        }
    }

    public void observe(ObjectId id, String displayName) {
        mongo.updateFirst(
            Query.query(Criteria.where("_id").is(id).and("nameCustomized").ne(true)),
            new Update().set("displayName", displayName).set("updatedAt", new Date()),
            "contacts"
        );
    }
}
