package com.wxn.chatbot.conversation.document;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.OffsetDateTime;

@Document(collection = "conversations")
public class Conversation {

    @Id
    private ObjectId id;

    @Field("companyId")
    private Long companyId;

    @Field("status")
    private String status;

    @Field("createdAt")
    private OffsetDateTime createdAt;

    @Field("updatedAt")
    private OffsetDateTime updatedAt;

    protected Conversation() {
    }
}
