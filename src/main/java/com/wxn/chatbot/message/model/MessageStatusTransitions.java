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
                NormalizedMessageStatus.UNKNOWN,
                NormalizedMessageStatus.SIMULATED
            );
            case DELIVERED -> List.of(
                NormalizedMessageStatus.PENDING,
                NormalizedMessageStatus.ACCEPTED,
                NormalizedMessageStatus.SENT,
                NormalizedMessageStatus.UNKNOWN,
                NormalizedMessageStatus.SIMULATED
            );
            case READ -> List.of(
                NormalizedMessageStatus.PENDING,
                NormalizedMessageStatus.ACCEPTED,
                NormalizedMessageStatus.SENT,
                NormalizedMessageStatus.DELIVERED,
                NormalizedMessageStatus.UNKNOWN,
                NormalizedMessageStatus.SIMULATED
            );
            case FAILED -> List.of(
                NormalizedMessageStatus.PENDING,
                NormalizedMessageStatus.ACCEPTED,
                NormalizedMessageStatus.SENT,
                NormalizedMessageStatus.UNKNOWN,
                NormalizedMessageStatus.SIMULATED
            );
            case UNKNOWN -> List.of(NormalizedMessageStatus.PENDING);
            default -> List.of();
        };
    }
}
