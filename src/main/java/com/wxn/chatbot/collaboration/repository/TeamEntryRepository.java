package com.wxn.chatbot.collaboration.repository;

import com.wxn.chatbot.collaboration.document.TeamEntry;
import java.util.*;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TeamEntryRepository extends MongoRepository<TeamEntry, ObjectId> {
    List<TeamEntry> findByConversationIdAndKindAndDeletedAtIsNullOrderByPinnedDescCreatedAtDesc(
        ObjectId id,
        String kind,
        Pageable page
    );
    Optional<TeamEntry> findByConversationIdAndAuthorUserIdAndOperationId(
        ObjectId conversation,
        Long author,
        String operation
    );
}
