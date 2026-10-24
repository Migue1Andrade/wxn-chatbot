package com.wxn.chatbot.messagelog.repository;

import com.wxn.chatbot.messagelog.document.MessageLog;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MessageLogRepository extends MongoRepository<MessageLog, ObjectId> {
    boolean existsByEventKeyAndCompanyId(String eventKey, Long companyId);
    List<MessageLog> findByCompanyIdAndChannelIdAndExternalMessageIdAndEventOrderByCreatedAtAsc(
        Long companyId,
        Long channelId,
        String externalMessageId,
        String event
    );
}
