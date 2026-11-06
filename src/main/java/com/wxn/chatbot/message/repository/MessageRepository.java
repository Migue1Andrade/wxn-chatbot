package com.wxn.chatbot.message.repository;

import com.wxn.chatbot.message.document.Message;
import java.util.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MessageRepository extends MongoRepository<Message, ObjectId>, MessageOperations {
    @org.springframework.data.mongodb.repository.Query(
        "{'direction':'OUTBOUND','status':{'$in':['PENDING','UNKNOWN','ACCEPTED']},'$or':[{'lastReconciledAt':null},{'lastReconciledAt':{'$lt':?0}}]}"
    )
    List<Message> reconciliationCandidates(
        java.util.Date before,
        org.springframework.data.domain.Pageable pageable
    );

    Optional<Message> findByChannelIdAndOperationId(Long channelId, String operationId);
    Optional<Message> findByChannelIdAndProviderTrackingId(Long channelId, String providerTrackingId);
    List<Message> findByChannelIdAndDirectionAndStatusIn(
        Long channelId,
        String direction,
        java.util.Collection<com.wxn.chatbot.whatsapp.model.NormalizedMessageStatus> statuses
    );
    Optional<Message> findByIdAndConversationId(ObjectId id, ObjectId conversationId);
    Optional<Message> findByChannelIdAndExternalMessageId(Long channelId, String externalMessageId);
    List<Message> findByConversationIdOrderByCreatedAtAsc(
        ObjectId conversationId,
        org.springframework.data.domain.Pageable pageable
    );
}
