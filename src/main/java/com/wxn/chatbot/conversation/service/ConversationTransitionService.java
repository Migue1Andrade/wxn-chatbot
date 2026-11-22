package com.wxn.chatbot.conversation.service;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.event.ConversationEventPublisher;
import com.wxn.chatbot.conversation.event.ConversationEventTypes;
import com.wxn.chatbot.conversation.mapper.ConversationMapper;
import com.wxn.chatbot.conversation.model.ConversationActivityType;
import com.wxn.chatbot.conversation.model.ConversationTransition;
import com.wxn.chatbot.conversation.repository.ConversationRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ConversationTransitionService {

    private final ConversationWriteCoordinator writes;
    private final ConversationRepository repository;
    private final ConversationActivityService activities;
    private final ConversationEventPublisher events;

    public ConversationTransitionService(
        ConversationRepository repository,
        ConversationActivityService activities,
        ConversationEventPublisher events,
        ConversationWriteCoordinator writes
    ) {
        this.writes = writes;
        this.repository = repository;
        this.activities = activities;
        this.events = events;
    }

    public Conversation apply(Conversation current, long expectedVersion, ConversationTransition transition) {
        return attempt(current, expectedVersion, transition).orElseThrow(() ->
            ApiException.conflict("Atendimento alterado por outro cliente; atualize antes de repetir")
        );
    }

    public Optional<Conversation> attempt(
        Conversation current,
        long expectedVersion,
        ConversationTransition transition
    ) {
        var activity = activities.prepare(
            current,
            transition.activity(),
            transition.actorMemberId(),
            transition.memberId(),
            transition.reason(),
            expectedVersion + 1,
            OffsetDateTime.now()
        );
        var updated = writes.execute(current.getId().toHexString(), () ->
            repository.changeState(
                current,
                expectedVersion,
                transition.status(),
                transition.memberId(),
                transition.closedAt(),
                activity
            )
        );
        updated.ifPresent(conversation -> published(current, conversation, transition.eventType()));
        return updated;
    }

    /** Devolve a conversa à fila de atendimento, opcionalmente com setor, e inicia a espera por um atendente. */
    public Optional<Conversation> returnToQueue(
        Conversation current,
        long expectedVersion,
        Long departmentId,
        ConversationActivityType activityType,
        Long actorMemberId,
        String reason
    ) {
        var activity = activities.prepare(
            current,
            activityType,
            actorMemberId,
            null,
            reason,
            expectedVersion + 1,
            OffsetDateTime.now()
        );
        var updated = writes.execute(current.getId().toHexString(), () ->
            repository.returnToQueue(current, expectedVersion, departmentId, activity)
        );
        updated.ifPresent(conversation ->
            published(current, conversation, ConversationEventTypes.CONVERSATION_UPDATED)
        );
        return updated;
    }

    private void published(Conversation current, Conversation conversation, String eventType) {
        activities.flush(conversation);
        if (
            current.getAssignedMemberId() != null &&
            !java.util.Objects.equals(current.getAssignedMemberId(), conversation.getAssignedMemberId())
        ) {
            events.publish(
                ConversationEventTypes.ACCESS_REVOKED,
                new com.wxn.chatbot.conversation.event.AccessRevoked(
                    current.getId().toHexString(),
                    current.getAssignedMemberId()
                )
            );
        }
        events.publish(eventType, ConversationMapper.response(conversation));
    }
}
