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

    private final com.wxn.chatbot.member.repository.MemberRepository members;
    private final com.wxn.chatbot.notification.service.NotificationService notifications;
    private final ConversationActivityRepository repository;
    private final ConversationRepository conversations;

    public ConversationActivityService(
        ConversationActivityRepository repository,
        ConversationRepository conversations,
        com.wxn.chatbot.member.repository.MemberRepository members,
        com.wxn.chatbot.notification.service.NotificationService notifications
    ) {
        this.members = members;
        this.notifications = notifications;
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
        String key = conversation.getId().toHexString() + ":" + version + ":" + event;
        return new ConversationActivity(
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
                repository.findByEventKey(activity.getEventKey()).orElseThrow(() -> duplicate);
            }
            if (
                activity.getNewMemberId() != null &&
                java.util.Set.of(
                    ConversationActivityType.MEMBER_ASSIGNED,
                    ConversationActivityType.MEMBER_ASSUMED,
                    ConversationActivityType.MEMBER_TRANSFERRED
                ).contains(activity.getEvent())
            ) {
                members
                    .findById(activity.getNewMemberId())
                    .filter(member -> member.getUserId() != null)
                    .ifPresent(member ->
                        notifications.createOnce(
                            member.getUserId(),
                            activity.getEvent() == ConversationActivityType.MEMBER_TRANSFERRED
                                ? "CONVERSATION_TRANSFERRED"
                                : "CONVERSATION_ASSIGNED",
                            "Um atendimento foi atribuído a você",
                            "CONVERSATION",
                            conversation.getId().toHexString(),
                            activity.getEventKey() + ":" + member.getUserId()
                        )
                    );
            }
            conversations.acknowledgeActivity(conversation.getId(), activity.getId());
        }
        conversation.setPendingActivities(List.of());
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 15000)
    public void replayPendingNotifications() {
        for (var conversation : conversations.pendingNotifications(
            org.springframework.data.domain.PageRequest.of(0, 25)
        )) {
            flush(conversation);
        }
    }

    public List<ConversationManagementDtos.Activity> recent(Conversation conversation, int page, int size) {
        flush(conversation);
        return repository
            .findByConversationIdOrderByCreatedAtDescIdDesc(
                conversation.getId(),
                ConversationService.page(page, size)
            )
            .stream()
            .map(ConversationActivityMapper::response)
            .toList();
    }

    public List<ConversationManagementDtos.Activity> list(Conversation conversation, int page, int size) {
        flush(conversation);
        return repository
            .findByConversationIdOrderByCreatedAtAscIdAsc(
                conversation.getId(),
                ConversationService.page(page, size)
            )
            .stream()
            .map(ConversationActivityMapper::response)
            .toList();
    }
}
