package com.wxn.chatbot.websocket;

import com.wxn.chatbot.conversation.event.*;
import com.wxn.chatbot.message.document.Message;
import com.wxn.chatbot.message.mapper.MessageMapper;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ConversationEventsTest {
    @Test void envelopesAreCanonicalUniqueAndTenantScoped() {
        var spring = mock(ApplicationEventPublisher.class);
        var publisher = new LocalConversationEventPublisher(spring);
        var document = new Message(); document.setId(new ObjectId()); document.setCompanyId(1L); document.setConversationId(new ObjectId());
        var payload = MessageMapper.response(document);
        publisher.publish(ConversationEventTypes.MESSAGE_RECEIVED, 1L, payload);
        publisher.publish(ConversationEventTypes.MESSAGE_RECEIVED, 1L, payload);
        var events = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(spring, times(2)).publishEvent(events.capture());
        var first = (ConversationEvent) events.getAllValues().get(0);
        var second = (ConversationEvent) events.getAllValues().get(1);
        assertThat(first.eventId()).isNotEqualTo(second.eventId());
        assertThat(first.conversationId()).isEqualTo(payload.conversationId());
        assertThat(first.payload()).isSameAs(payload);
        assertThatThrownBy(() -> publisher.publish(ConversationEventTypes.MESSAGE_RECEIVED, 2L, payload)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publisher.publish("unknown", 1L, payload)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publisher.publish(ConversationEventTypes.MESSAGE_RECEIVED, 1L, document)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ConversationEventTypes.ALL).hasSize(10);
    }
}
