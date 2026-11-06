package com.wxn.chatbot.conversation.mapper;

import com.wxn.chatbot.conversation.document.ConversationActivity;
import com.wxn.chatbot.conversation.dto.ConversationManagementDtos;

public final class ConversationActivityMapper {

    private ConversationActivityMapper() {}

    public static ConversationManagementDtos.Activity response(ConversationActivity event) {
        return new ConversationManagementDtos.Activity(
            event.getId().toHexString(),
            event.getConversationId().toHexString(),
            event.getEvent(),
            event.getActorMemberId(),
            event.getPreviousMemberId(),
            event.getNewMemberId(),
            event.getReason(),
            event.getCreatedAt().toInstant()
        );
    }
}
