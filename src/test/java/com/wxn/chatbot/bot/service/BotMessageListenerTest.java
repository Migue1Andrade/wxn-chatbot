package com.wxn.chatbot.bot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wxn.chatbot.bot.config.BotConfiguration;
import com.wxn.chatbot.conversation.event.ConversationEvent;
import com.wxn.chatbot.conversation.event.ConversationEventTypes;
import com.wxn.chatbot.whatsapp.model.NormalizedMessage;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

class BotMessageListenerTest {

    private static final String API_KEY = "test-key-with-at-least-32-characters";
    private static final Executor DIRECT = Runnable::run;

    private final BotService bot = mock(BotService.class);
    private final NormalizedMessage message = mock(NormalizedMessage.class);

    @Test
    void receivedMessageIsHandedToTheBot() {
        listener(true, DIRECT).onConversationEvent(event(ConversationEventTypes.MESSAGE_RECEIVED));

        verify(bot).handleInbound(message);
    }

    @Test
    void otherEventsAndDisabledBotAreIgnored() {
        listener(true, DIRECT).onConversationEvent(event(ConversationEventTypes.MESSAGE_SENT));
        listener(false, DIRECT).onConversationEvent(event(ConversationEventTypes.MESSAGE_RECEIVED));

        verifyNoInteractions(bot);
    }

    @Test
    void botFailuresNeverReachTheInboundFlow() {
        doThrow(new IllegalStateException("falha")).when(bot).handleInbound(message);
        Executor full = task -> {
            throw new RejectedExecutionException("cheia");
        };

        var event = event(ConversationEventTypes.MESSAGE_RECEIVED);
        assertThatCode(() -> listener(true, DIRECT).onConversationEvent(event)).doesNotThrowAnyException();
        assertThatCode(() -> listener(true, full).onConversationEvent(event)).doesNotThrowAnyException();
    }

    private BotMessageListener listener(boolean enabled, Executor executor) {
        var config = new BotConfiguration(enabled, "http://bot:8000", API_KEY, 7L, 45);
        return new BotMessageListener(config, bot, executor);
    }

    private ConversationEvent event(String type) {
        return new ConversationEvent(UUID.randomUUID(), type, "c-1", Instant.now(), message);
    }
}
