package com.wxn.chatbot.messagelog.document;

import com.wxn.chatbot.whatsapp.model.*;
import java.time.OffsetDateTime;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "message_logs")
public class MessageLog {

    @Id
    private ObjectId id;

    @Field("messageId")
    private ObjectId messageId;

    @Field("event")
    private String event;

    @Field("createdAt")
    private OffsetDateTime createdAt;

    @Field("channelId")
    private Long channelId;

    @Field("externalMessageId")
    private String externalMessageId;

    @Field("status")
    private NormalizedMessageStatus status;

    @Field("errorCode")
    private String errorCode;

    @Field("eventKey")
    private String eventKey;

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String value) {
        eventKey = value;
    }

    private org.bson.Document echo;

    public org.bson.Document getEcho() {
        return echo;
    }

    public void setEcho(org.bson.Document value) {
        echo = value;
    }

    public MessageLog() {}

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId value) {
        id = value;
    }

    public ObjectId getMessageId() {
        return messageId;
    }

    public void setMessageId(ObjectId value) {
        messageId = value;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String value) {
        event = value;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime value) {
        createdAt = value;
    }

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long value) {
        channelId = value;
    }

    public String getExternalMessageId() {
        return externalMessageId;
    }

    public void setExternalMessageId(String value) {
        externalMessageId = value;
    }

    public NormalizedMessageStatus getStatus() {
        return status;
    }

    public void setStatus(NormalizedMessageStatus value) {
        status = value;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String value) {
        errorCode = value;
    }
}
