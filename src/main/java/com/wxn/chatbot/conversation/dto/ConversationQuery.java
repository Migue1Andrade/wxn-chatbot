package com.wxn.chatbot.conversation.dto;

import com.wxn.chatbot.conversation.model.ConversationStatus;
import jakarta.validation.constraints.*;

public record ConversationQuery(
    @Min(0) Integer page,
    @Min(1) @Max(200) Integer size,
    ConversationStatus status,
    @Positive Long assignedMemberId,
    Boolean unassigned,
    @Positive Long readerMemberId,
    @Size(max = 100) String search
) {
    public int pageNumber() {
        return page == null ? 0 : page;
    }

    public int pageSize(int fallback) {
        return size == null ? fallback : size;
    }

    public boolean onlyUnassigned() {
        return Boolean.TRUE.equals(unassigned);
    }
}
