package com.wxn.chatbot.websocket;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wxn.chatbot.conversation.event.*;
import com.wxn.chatbot.message.document.Message;
import com.wxn.chatbot.message.mapper.MessageMapper;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ConversationEventsTest {

    @Test
    void envelopesAreCanonicalUnique() {
        var spring = mock(ApplicationEventPublisher.class);
        var publisher = new LocalConversationEventPublisher(spring);
        var document = new Message();
        document.setId(new ObjectId());

        document.setConversationId(new ObjectId());
        var payload = MessageMapper.response(document);
        publisher.publish(ConversationEventTypes.MESSAGE_RECEIVED, payload);
        publisher.publish(ConversationEventTypes.MESSAGE_RECEIVED, payload);
        var events = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(spring, times(2)).publishEvent(events.capture());
        var first = (ConversationEvent) events.getAllValues().get(0);
        var second = (ConversationEvent) events.getAllValues().get(1);
        assertThat(first.eventId()).isNotEqualTo(second.eventId());
        assertThat(first.conversationId()).isEqualTo(payload.conversationId());
        assertThat(first.payload()).isSameAs(payload);
        assertThatThrownBy(() -> publisher.publish("unknown", payload)).isInstanceOf(
            IllegalArgumentException.class
        );
        assertThatThrownBy(() ->
            publisher.publish(ConversationEventTypes.MESSAGE_RECEIVED, document)
        ).isInstanceOf(IllegalArgumentException.class);
        assertThat(ConversationEventTypes.ALL)
            .hasSize(13)
            .contains(
                ConversationEventTypes.ACCESS_REVOKED,
                ConversationEventTypes.TEAM_UPDATED,
                "kanban.metrics.updated"
            );
    }
}
