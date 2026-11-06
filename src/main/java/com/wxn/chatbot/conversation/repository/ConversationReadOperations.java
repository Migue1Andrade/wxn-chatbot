package com.wxn.chatbot.conversation.repository;

import com.wxn.chatbot.conversation.document.ConversationRead;
import com.wxn.chatbot.message.model.MessageCursor;
import java.util.*;
import org.bson.types.ObjectId;

public interface ConversationReadOperations {
    Optional<ConversationRead> advance(ObjectId conversationId, Long memberId, MessageCursor cursor);
    Map<ObjectId, Long> unreadCounts(List<ObjectId> conversationIds, List<ConversationRead> reads);
}
