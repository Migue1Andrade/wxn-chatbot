package com.wxn.chatbot.message.model;

import com.wxn.chatbot.whatsapp.model.NormalizedMessageStatus;
import java.util.List;

public final class MessageStatusTransitions {

    private MessageStatusTransitions() {}

    public static List<NormalizedMessageStatus> allowedPrevious(NormalizedMessageStatus next) {
        return switch (next) {
            case ACCEPTED, SIMULATED -> List.of(NormalizedMessageStatus.PENDING);
            case SENT -> List.of(
                NormalizedMessageStatus.PENDING,
                NormalizedMessageStatus.ACCEPTED,
                NormalizedMessageStatus.UNKNOWN
            );
            case DELIVERED -> List.of(
                NormalizedMessageStatus.PENDING,
                NormalizedMessageStatus.ACCEPTED,
                NormalizedMessageStatus.SENT,
                NormalizedMessageStatus.UNKNOWN
            );
            case READ -> List.of(
                NormalizedMessageStatus.PENDING,
                NormalizedMessageStatus.ACCEPTED,
                NormalizedMessageStatus.SENT,
                NormalizedMessageStatus.DELIVERED,
                NormalizedMessageStatus.UNKNOWN
            );
            case FAILED -> List.of(
                NormalizedMessageStatus.PENDING,
                NormalizedMessageStatus.ACCEPTED,
                NormalizedMessageStatus.SENT,
                NormalizedMessageStatus.UNKNOWN
            );
            case UNKNOWN -> List.of(NormalizedMessageStatus.PENDING);
            default -> List.of();
        };
    }
}
