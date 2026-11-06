package com.wxn.chatbot.conversation.event;

public record AccessRevoked(String conversationId, Long memberId) implements CanonicalEventPayload {}
