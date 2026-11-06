package com.wxn.chatbot.message.model;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.message.document.Message;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Base64;
import org.bson.types.ObjectId;

public record MessageCursor(OffsetDateTime timestamp, ObjectId id) {
    public static MessageCursor of(Message message) {
        return new MessageCursor(
            message.getCreatedAt() == null ? Instant.EPOCH.atOffset(ZoneOffset.UTC) : message.getCreatedAt(),
            message.getId()
        );
    }

    public String encode() {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                (timestamp.toInstant().toEpochMilli() + ":" + id.toHexString()).getBytes(
                    StandardCharsets.UTF_8
                )
            );
    }

    public static MessageCursor decode(String value) {
        try {
            if (value.length() > 100) {
                throw new IllegalArgumentException();
            }
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split(
                ":"
            );
            if (parts.length != 2 || !ObjectId.isValid(parts[1])) {
                throw new IllegalArgumentException();
            }
            return new MessageCursor(
                Instant.ofEpochMilli(Long.parseLong(parts[0])).atOffset(ZoneOffset.UTC),
                new ObjectId(parts[1])
            );
        } catch (RuntimeException invalid) {
            throw ApiException.badRequest("Cursor de mensagem inválido");
        }
    }
}
