package com.wxn.chatbot.messagelog.repository;

import com.wxn.chatbot.messagelog.document.MessageLog;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MessageLogRepository extends MongoRepository<MessageLog, ObjectId> {
    @org.springframework.data.mongodb.repository.Query(
        "{'channelId':?0, 'event':'provider.delivery', 'echo.tracking':?1}"
    )
    List<MessageLog> tracked(Long channelId, String tracking);

    boolean existsByEventKey(String eventKey);
    List<MessageLog> findByChannelIdAndExternalMessageIdAndEventOrderByCreatedAtAsc(
        Long channelId,
        String externalMessageId,
        String event
    );
}
