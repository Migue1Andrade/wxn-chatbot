package com.wxn.chatbot.message.repository;

import com.wxn.chatbot.message.document.Message;
import com.wxn.chatbot.message.model.MessageCursor;
import com.wxn.chatbot.whatsapp.model.NormalizedMessageStatus;
import java.time.Instant;
import java.util.*;
import java.util.List;

public interface MessageOperations {
    void reconciliationChecked(com.wxn.chatbot.message.document.Message message);
    void providerIds(Message message, String trackingId, String responseId);
    boolean claimDeletion(Message message, String operationId, String intent, Long userId);
    Message finishDeletion(Message message, String state);
    Optional<Message> applyRemoteEdit(Message message, String value, Instant at);
    Optional<Message> confirmPendingEdit(Message message, String value);
    Optional<Message> applyRemoteDeletion(Message message, Instant at);

    List<Message> before(org.bson.types.ObjectId conversationId, MessageCursor cursor, int limit);
    boolean completeInbound(Message message);
    void associateExternalId(Message message, String externalId, boolean simulated);
    Optional<Message> updateStatus(
        Message message,
        NormalizedMessageStatus status,
        String errorCode,
        Collection<NormalizedMessageStatus> allowedPrevious
    );
}
