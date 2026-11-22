package com.wxn.chatbot.bot.service;

import com.wxn.chatbot.bot.config.BotConfiguration;
import com.wxn.chatbot.bot.config.BotExecutorConfiguration;
import com.wxn.chatbot.conversation.event.ConversationEvent;
import com.wxn.chatbot.conversation.event.ConversationEventTypes;
import com.wxn.chatbot.whatsapp.model.NormalizedMessage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Repassa cada mensagem recebida ao bot em segundo plano, sem atrasar o fluxo de entrada. */
@Component
public class BotMessageListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(BotMessageListener.class);

    private final BotConfiguration config;
    private final BotService bot;
    private final Executor executor;

    public BotMessageListener(
        BotConfiguration config,
        BotService bot,
        @Qualifier(BotExecutorConfiguration.BOT_EXECUTOR) Executor executor
    ) {
        this.config = config;
        this.bot = bot;
        this.executor = executor;
    }

    @EventListener
    public void onConversationEvent(ConversationEvent event) {
        if (
            !config.enabled() ||
            !ConversationEventTypes.MESSAGE_RECEIVED.equals(event.type()) ||
            !(event.payload() instanceof NormalizedMessage message)
        ) {
            return;
        }
        try {
            executor.execute(() -> handle(message));
        } catch (RejectedExecutionException error) {
            LOGGER.warn("Fila do bot cheia; mensagem {} não será respondida pelo bot", message.id());
        }
    }

    private void handle(NormalizedMessage message) {
        try {
            bot.handleInbound(message);
        } catch (RuntimeException error) {
            LOGGER.error("Falha inesperada do bot na conversa {}", message.conversationId(), error);
        }
    }
}
