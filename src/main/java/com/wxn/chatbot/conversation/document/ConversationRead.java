package com.wxn.chatbot.conversation.document;

import java.time.OffsetDateTime;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("conversation_reads")
public class ConversationRead {

    @Id
    private ObjectId id;

    private Long companyId;
    private ObjectId conversationId;
    private Long memberId;
    private OffsetDateTime lastReadAt;
    private ObjectId throughMessageId;

    public Long getCompanyId() {
        return companyId;
    }

    public ObjectId getConversationId() {
        return conversationId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public OffsetDateTime getLastReadAt() {
        return lastReadAt;
    }

    public ObjectId getThroughMessageId() {
        return throughMessageId;
    }
}
