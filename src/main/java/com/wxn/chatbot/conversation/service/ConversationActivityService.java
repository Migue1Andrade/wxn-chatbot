package com.wxn.chatbot.conversation.service;

import com.wxn.chatbot.conversation.document.*;
import com.wxn.chatbot.conversation.dto.ConversationManagementDtos;
import com.wxn.chatbot.conversation.mapper.ConversationActivityMapper;
import com.wxn.chatbot.conversation.model.ConversationActivityType;
import com.wxn.chatbot.conversation.repository.*;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ConversationActivityService {

    private final ConversationActivityRepository repository;
    private final ConversationRepository conversations;

    public ConversationActivityService(
        ConversationActivityRepository repository,
        ConversationRepository conversations
    ) {
        this.repository = repository;
        this.conversations = conversations;
    }

    public ConversationActivity prepare(
        Conversation conversation,
        ConversationActivityType event,
        Long actor,
        Long next,
        String reason,
        long version,
        OffsetDateTime occurredAt
    ) {
        String key =
            conversation.getCompanyId() +
            ":" +
            conversation.getId().toHexString() +
            ":" +
            version +
            ":" +
            event;
        return new ConversationActivity(
            conversation.getCompanyId(),
            conversation.getId(),
            key,
            event,
            actor,
            conversation.getAssignedMemberId(),
            next,
            reason,
            occurredAt
        );
    }

    public void flush(Conversation conversation) {
        for (ConversationActivity activity : conversation.getPendingActivities()) {
            try {
                repository.insert(activity);
            } catch (DuplicateKeyException duplicate) {
                repository
                    .findByEventKeyAndCompanyId(activity.getEventKey(), activity.getCompanyId())
                    .orElseThrow(() -> duplicate);
            }
            conversations.acknowledgeActivity(
                conversation.getId(),
                conversation.getCompanyId(),
                activity.getId()
            );
        }
        conversation.setPendingActivities(List.of());
    }

    public List<ConversationManagementDtos.Activity> list(Conversation conversation, int page, int size) {
        flush(conversation);
        return repository
            .findByCompanyIdAndConversationIdOrderByCreatedAtAscIdAsc(
                conversation.getCompanyId(),
                conversation.getId(),
                ConversationService.page(page, size)
            )
            .stream()
            .map(ConversationActivityMapper::response)
            .toList();
    }
}
