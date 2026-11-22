package com.wxn.chatbot.conversation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.document.ConversationActivity;
import com.wxn.chatbot.conversation.event.AccessRevoked;
import com.wxn.chatbot.conversation.event.CanonicalEventPayload;
import com.wxn.chatbot.conversation.event.ConversationEventPublisher;
import com.wxn.chatbot.conversation.event.ConversationEventTypes;
import com.wxn.chatbot.conversation.model.ConversationActivityType;
import com.wxn.chatbot.conversation.model.ConversationStatus;
import com.wxn.chatbot.conversation.repository.ConversationOperationsImpl;
import com.wxn.chatbot.conversation.repository.ConversationRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class ConversationTransitionServiceTest {

    private static final long BOT_MEMBER_ID = 7L;

    private ConversationRepository repository;
    private ConversationActivityService activities;
    private ConversationEventPublisher events;
    private ConversationTransitionService service;
    private ConversationActivity activity;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        repository = mock(ConversationRepository.class);
        activities = mock(ConversationActivityService.class);
        events = mock(ConversationEventPublisher.class);
        var writes = mock(ConversationWriteCoordinator.class);
        when(writes.execute(anyString(), any())).thenAnswer(call ->
            ((Supplier<Object>) call.getArgument(1)).get()
        );
        service = new ConversationTransitionService(repository, activities, events, writes);
        activity = mock(ConversationActivity.class);
        when(activity.getCreatedAt()).thenReturn(OffsetDateTime.now());
        when(
            activities.prepare(
                any(),
                eq(ConversationActivityType.BOT_HANDOFF),
                any(),
                any(),
                any(),
                anyLong(),
                any()
            )
        ).thenReturn(activity);
    }

    @Test
    void returnToQueueRecordsActivityAndRevokesPreviousOwner() {
        var current = conversation(BOT_MEMBER_ID);
        var queued = conversation(null);
        when(repository.returnToQueue(current, 4L, 3L, activity)).thenReturn(Optional.of(queued));

        var result = service.returnToQueue(
            current,
            4L,
            3L,
            ConversationActivityType.BOT_HANDOFF,
            BOT_MEMBER_ID,
            "Setor Financeiro: cobrança duplicada"
        );

        assertThat(result).contains(queued);
        verify(activities).prepare(
            eq(current),
            eq(ConversationActivityType.BOT_HANDOFF),
            eq(BOT_MEMBER_ID),
            isNull(),
            eq("Setor Financeiro: cobrança duplicada"),
            eq(5L),
            any(OffsetDateTime.class)
        );
        verify(activities).flush(queued);
        verify(events).publish(eq(ConversationEventTypes.ACCESS_REVOKED), any(AccessRevoked.class));
        verify(events).publish(
            eq(ConversationEventTypes.CONVERSATION_UPDATED),
            any(CanonicalEventPayload.class)
        );
    }

    @Test
    void returnToQueueWithStaleVersionChangesNothing() {
        var current = conversation(BOT_MEMBER_ID);
        when(repository.returnToQueue(any(), anyLong(), any(), any())).thenReturn(Optional.empty());

        var result = service.returnToQueue(
            current,
            4L,
            3L,
            ConversationActivityType.BOT_HANDOFF,
            BOT_MEMBER_ID,
            "motivo"
        );

        assertThat(result).isEmpty();
        verify(activities, never()).flush(any());
        verifyNoInteractions(events);
    }

    @Test
    void returnToQueueUpdateIsVersionedAndStartsWaitingForAgent() {
        var mongo = mock(MongoTemplate.class);
        var current = conversation(BOT_MEMBER_ID);
        var at = OffsetDateTime.parse("2026-11-21T10:00:00Z");
        var handoff = mock(ConversationActivity.class);
        when(handoff.getCreatedAt()).thenReturn(at);

        new ConversationOperationsImpl(mongo).returnToQueue(current, 4L, 3L, handoff);

        var query = ArgumentCaptor.forClass(Query.class);
        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(
            query.capture(),
            update.capture(),
            any(FindAndModifyOptions.class),
            eq(Conversation.class)
        );
        assertThat(query.getValue().getQueryObject().get("version")).isEqualTo(4L);
        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        assertThat(set.get("status")).isEqualTo(ConversationStatus.PENDING.name());
        assertThat(set.containsKey("assignedMemberId")).isTrue();
        assertThat(set.get("assignedMemberId")).isNull();
        assertThat(set.get("departmentId")).isEqualTo(3L);
        assertThat(set.get("waitingForAgentSince")).isEqualTo(at);
        assertThat(set.get("version")).isEqualTo(5L);
    }

    @Test
    void returnToQueueWithoutDepartmentKeepsTheCurrentOne() {
        var mongo = mock(MongoTemplate.class);
        var handoff = mock(ConversationActivity.class);
        when(handoff.getCreatedAt()).thenReturn(OffsetDateTime.now());

        new ConversationOperationsImpl(mongo).returnToQueue(conversation(BOT_MEMBER_ID), 0L, null, handoff);

        var update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(
            any(Query.class),
            update.capture(),
            any(FindAndModifyOptions.class),
            eq(Conversation.class)
        );
        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        assertThat(set.containsKey("departmentId")).isFalse();
    }

    private static Conversation conversation(Long assignedMemberId) {
        var conversation = new Conversation();
        conversation.setId(new ObjectId());
        conversation.setStatus(ConversationStatus.IN_PROGRESS.name());
        conversation.setAssignedMemberId(assignedMemberId);
        conversation.setVersion(4L);
        return conversation;
    }
}
