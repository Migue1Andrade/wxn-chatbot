package com.wxn.chatbot.conversation.service;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.event.ConversationEventPublisher;
import com.wxn.chatbot.conversation.mapper.ConversationMapper;
import com.wxn.chatbot.conversation.model.ConversationTransition;
import com.wxn.chatbot.conversation.repository.ConversationRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ConversationTransitionService {

    private final ConversationRepository repository;
    private final ConversationActivityService activities;
    private final ConversationEventPublisher events;

    public ConversationTransitionService(
        ConversationRepository repository,
        ConversationActivityService activities,
        ConversationEventPublisher events
    ) {
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
        var updated = repository.changeState(
            current,
            expectedVersion,
            transition.status(),
            transition.memberId(),
            transition.closedAt(),
            activity
        );
        updated.ifPresent(conversation -> {
            activities.flush(conversation);
            events.publish(
                transition.eventType(),
                conversation.getCompanyId(),
                ConversationMapper.response(conversation)
            );
        });
        return updated;
    }
}
