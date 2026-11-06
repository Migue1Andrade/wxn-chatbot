package com.wxn.chatbot.websocket;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.stereotype.Component;

/** The simple broker has no receipts: acknowledge only after it registered a subscription. */
@Component
public class SubscriptionReceiptInterceptor implements ExecutorChannelInterceptor {

    private final ObjectProvider<MessageChannel> outbound;

    public SubscriptionReceiptInterceptor(
        @Qualifier("clientOutboundChannel") ObjectProvider<MessageChannel> outbound
    ) {
        this.outbound = outbound;
    }

    @Override
    public void afterMessageHandled(
        Message<?> message,
        MessageChannel channel,
        MessageHandler handler,
        Exception error
    ) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (
            error != null ||
            !(handler instanceof SimpleBrokerMessageHandler) ||
            headers == null ||
            headers.getCommand() != StompCommand.SUBSCRIBE ||
            headers.getReceipt() == null
        ) {
            return;
        }
        var receipt = StompHeaderAccessor.create(StompCommand.RECEIPT);
        receipt.setSessionId(headers.getSessionId());
        receipt.setReceiptId(headers.getReceipt());
        outbound.getObject().send(MessageBuilder.createMessage(new byte[0], receipt.getMessageHeaders()));
    }
}
