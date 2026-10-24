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
        log.setCompanyId(message.getCompanyId());
        log.setChannelId(message.getChannelId());
        log.setMessageId(message.getId());
        log.setExternalMessageId(message.getExternalMessageId());
        log.setEvent(event.value());
        log.setStatus(message.getStatus());
        log.setErrorCode(message.getErrorCode());
        log.setCreatedAt(OffsetDateTime.now());
        log.setEventKey(message.getCompanyId() + ":" + message.getId().toHexString() + ":" + event.value());
        insert(log);
    }

    public void callback(WhatsAppChannel channel, NormalizedStatusEvent event) {
        MessageLog log = new MessageLog();
        log.setCompanyId(channel.getCompanyId());
        log.setChannelId(channel.getId());
        log.setExternalMessageId(event.externalMessageId());
        log.setEvent(PROVIDER_STATUS);
        log.setStatus(event.status());
        log.setErrorCode(event.errorCode());
        log.setCreatedAt(OffsetDateTime.now());
        log.setEventKey(
            channel.getCompanyId() +
                ":" +
                channel.getId() +
                ":" +
                event.externalMessageId() +
                ":" +
                event.status() +
                ":" +
                event.errorCode()
        );
        insert(log);
    }

    private void insert(MessageLog log) {
        try {
            repository.insert(log);
        } catch (DuplicateKeyException duplicate) {
            if (
                !repository.existsByEventKeyAndCompanyId(log.getEventKey(), log.getCompanyId())
            ) throw duplicate;
        }
    }

    public List<MessageLog> callbacks(Message message) {
        return repository.findByCompanyIdAndChannelIdAndExternalMessageIdAndEventOrderByCreatedAtAsc(
            message.getCompanyId(),
            message.getChannelId(),
            message.getExternalMessageId(),
            PROVIDER_STATUS
        );
    }
}
