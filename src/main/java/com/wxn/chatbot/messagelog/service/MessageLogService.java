package com.wxn.chatbot.messagelog.service;

import com.wxn.chatbot.message.document.Message;
import com.wxn.chatbot.messagelog.document.MessageLog;
import com.wxn.chatbot.messagelog.model.MessageLogEvent;
import com.wxn.chatbot.messagelog.repository.MessageLogRepository;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.NormalizedStatusEvent;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class MessageLogService {

    private static final String PROVIDER_STATUS = "provider.status";
    private final MessageLogRepository repository;

    public MessageLogService(MessageLogRepository repository) {
        this.repository = repository;
    }

    public void record(Message message, MessageLogEvent event) {
        MessageLog log = new MessageLog();

        log.setChannelId(message.getChannelId());
        log.setMessageId(message.getId());
        log.setExternalMessageId(message.getExternalMessageId());
        log.setEvent(event.value());
        log.setStatus(message.getStatus());
        log.setErrorCode(message.getErrorCode());
        log.setCreatedAt(OffsetDateTime.now());
        log.setEventKey(
            message.getId().toHexString() +
                ":" +
                event.value() +
                (event.value().startsWith("message.delete.") ? ":" + message.getDeleteOperationId() : "")
        );
        if (event.value().startsWith("message.delete.")) {
            log.setEcho(
                new org.bson.Document("operationId", message.getDeleteOperationId())
                    .append("intent", message.getDeletionIntent())
                    .append("actorUserId", message.getDeletedByUserId())
            );
        }
        insert(log);
    }

    public void callback(WhatsAppChannel channel, NormalizedStatusEvent event) {
        MessageLog log = new MessageLog();

        log.setChannelId(channel.getId());
        log.setExternalMessageId(event.externalMessageId());
        log.setEvent(PROVIDER_STATUS);
        log.setStatus(event.status());
        log.setErrorCode(event.errorCode());
        log.setCreatedAt(OffsetDateTime.now());
        log.setEventKey(
            channel.getId() + ":" + event.externalMessageId() + ":" + event.status() + ":" + event.errorCode()
        );
        insert(log);
    }

    public void echo(
        WhatsAppChannel channel,
        com.wxn.chatbot.whatsapp.model.NormalizedInboundMessage incoming
    ) {
        MessageLog log = new MessageLog();
        log.setChannelId(channel.getId());
        log.setExternalMessageId(incoming.externalMessageId());
        log.setEvent("provider.echo");
        log.setEventKey(channel.getId() + ":" + incoming.externalMessageId() + ":echo");
        log.setCreatedAt(OffsetDateTime.now());
        log.setEcho(
            new org.bson.Document("text", incoming.content().get("text"))
                .append("phone", incoming.senderPhone())
                .append("occurredAt", incoming.occurredAt().toString())
        );
        insert(log);
    }

    public void webhook(WhatsAppChannel channel, String kind, com.fasterxml.jackson.databind.JsonNode body) {
        MessageLog log = new MessageLog();
        log.setChannelId(channel.getId());
        log.setEvent("provider.webhook." + kind);
        log.setCreatedAt(OffsetDateTime.now());
        String identity =
            body.path("type").asText() +
            ":" +
            body.path("messageId").asText() +
            ":" +
            body.path("ids").toString() +
            ":" +
            body.path("status").asText() +
            ":" +
            body.path("momment").asText();
        try {
            log.setEventKey(
                channel.getId() +
                    ":" +
                    kind +
                    ":" +
                    java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(
                            identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                        )
                    )
            );
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        log.setEcho(
            new org.bson.Document("type", body.path("type").asText())
                .append("status", body.path("status").asText())
                .append("ids", body.path("ids").toString())
                .append("momment", body.path("momment").asLong())
        );
        insert(log);
    }

    private void insert(MessageLog log) {
        try {
            repository.insert(log);
        } catch (DuplicateKeyException duplicate) {
            if (!repository.existsByEventKey(log.getEventKey())) {
                throw duplicate;
            }
        }
    }

    public void delivery(WhatsAppChannel channel, String tracking, NormalizedStatusEvent event) {
        if (tracking == null || tracking.isBlank()) {
            return;
        }
        var log = new MessageLog();
        log.setChannelId(channel.getId());
        log.setExternalMessageId(event.externalMessageId());
        log.setStatus(event.status());
        log.setErrorCode(event.errorCode());
        log.setEvent("provider.delivery");
        log.setEventKey(
            channel.getId() + ":delivery:" + tracking + ":" + event.externalMessageId() + ":" + event.status()
        );
        log.setCreatedAt(OffsetDateTime.now());
        log.setEcho(new org.bson.Document("tracking", tracking));
        insert(log);
    }

    public List<MessageLog> tracked(Message message) {
        return message.getProviderTrackingId() == null
            ? List.of()
            : repository.tracked(message.getChannelId(), message.getProviderTrackingId());
    }

    public List<MessageLog> echoes(Long channelId, String externalId) {
        return repository.findByChannelIdAndExternalMessageIdAndEventOrderByCreatedAtAsc(
            channelId,
            externalId,
            "provider.echo"
        );
    }

    public List<MessageLog> callbacks(Message message) {
        return repository.findByChannelIdAndExternalMessageIdAndEventOrderByCreatedAtAsc(
            message.getChannelId(),
            message.getExternalMessageId(),
            PROVIDER_STATUS
        );
    }
}
