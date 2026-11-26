package com.wxn.chatbot.conversation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.model.ConversationActivityType;
import com.wxn.chatbot.conversation.repository.ConversationActivityRepository;
import com.wxn.chatbot.conversation.repository.ConversationRepository;
import com.wxn.chatbot.member.entity.Member;
import com.wxn.chatbot.member.repository.MemberRepository;
import com.wxn.chatbot.notification.service.NotificationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class ConversationActivityServiceTest {

    @Test
    void preservesPendingHandoffUntilTheNotificationCanBePersisted() {
        var activities = mock(ConversationActivityRepository.class);
        var conversations = mock(ConversationRepository.class);
        var members = mock(MemberRepository.class);
        var notifications = mock(NotificationService.class);
        var service = new ConversationActivityService(activities, conversations, members, notifications);
        var conversation = new Conversation();
        var id = new ObjectId();
        conversation.setId(id);
        conversation.setStatus("PENDING");
        var activity = service.prepare(
            conversation,
            ConversationActivityType.BOT_HANDOFF_TO_QUEUE,
            1L,
            null,
            null,
            2L,
            OffsetDateTime.now()
        );
        conversation.setPendingActivities(List.of(activity));
        when(conversations.findById(id)).thenReturn(Optional.of(conversation));
        var recipient = mock(Member.class);
        when(recipient.getUserId()).thenReturn(7L);
        when(members.activeSupportHumans()).thenReturn(List.of(recipient));
        doThrow(new DataAccessResourceFailureException("Banco temporariamente indisponível"))
            .doNothing()
            .when(notifications)
            .offerConversation(id.toHexString(), activity.getEventKey(), List.of(7L));

        assertThatThrownBy(() -> service.flush(conversation)).isInstanceOf(
            DataAccessResourceFailureException.class
        );
        verify(conversations, never()).acknowledgeActivity(id, activity.getId());

        service.flush(conversation);

        verify(notifications, times(2)).offerConversation(
            id.toHexString(),
            activity.getEventKey(),
            List.of(7L)
        );
        verify(conversations).acknowledgeActivity(id, activity.getId());
    }
}
