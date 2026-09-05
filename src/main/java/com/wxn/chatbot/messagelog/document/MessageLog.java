package com.wxn.chatbot.messagelog.document;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.OffsetDateTime;

@Document(collection = "message_logs")
public class MessageLog {

    @Id
    private ObjectId id;

    @Field("companyId")
    private Long companyId;

    @Field("messageId")
    private ObjectId messageId;

    @Field("event")
    private String event;

    @Field("createdAt")
    private OffsetDateTime createdAt;

    protected MessageLog() {
    }
}
