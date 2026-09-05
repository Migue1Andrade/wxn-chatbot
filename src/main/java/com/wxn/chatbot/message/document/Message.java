package com.wxn.chatbot.message.document;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.OffsetDateTime;

@org.springframework.data.mongodb.core.mapping.Document(collection = "messages")
public class Message {

    @Id
    private ObjectId id;

    @Field("companyId")
    private Long companyId;

    @Field("conversationId")
    private ObjectId conversationId;

    @Field("direction")
    private String direction;

    @Field("type")
    private String type;

    @Field("content")
    private Document content;

    @Field("createdAt")
    private OffsetDateTime createdAt;

    protected Message() {
    }
}
