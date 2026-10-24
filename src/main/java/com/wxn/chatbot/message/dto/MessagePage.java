package com.wxn.chatbot.message.dto;

import com.wxn.chatbot.whatsapp.model.NormalizedMessage;
import java.util.List;

public record MessagePage(List<NormalizedMessage> items, String nextCursor, boolean hasMore) {}
