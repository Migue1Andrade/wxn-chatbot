package com.wxn.chatbot.conversation.dto;

import com.wxn.chatbot.whatsapp.model.NormalizedConversation;
import java.util.List;

public record ConversationPage(List<NormalizedConversation> items, Integer nextPage, boolean hasMore) {}
