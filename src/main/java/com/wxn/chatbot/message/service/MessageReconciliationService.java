package com.wxn.chatbot.message.service;

import com.wxn.chatbot.common.*;
import com.wxn.chatbot.message.document.Message;
import com.wxn.chatbot.message.mapper.MessageMapper;
import com.wxn.chatbot.message.repository.MessageRepository;
import com.wxn.chatbot.messagelog.service.MessageLogService;
import com.wxn.chatbot.whatsapp.model.*;
import com.wxn.chatbot.whatsapp.provider.WhatsAppProviderResolver;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MessageReconciliationService {

    private final MessageRepository repository;
    private final MessageLogService logs;
    private final MessageStatusService statuses;
    private final WhatsAppProviderResolver providers;

    public MessageReconciliationService(
        MessageRepository repository,
        MessageLogService logs,
        MessageStatusService statuses,
        WhatsAppProviderResolver providers
    ) {
        this.repository = repository;
        this.logs = logs;
        this.statuses = statuses;
        this.providers = providers;
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 15000)
    public void reconcilePending() {
        for (var message : repository.reconciliationCandidates(
            java.util.Date.from(java.time.Instant.now().minusSeconds(300)),
            org.springframework.data.domain.PageRequest.of(
                0,
                100,
                org.springframework.data.domain.Sort.by("createdAt")
            )
        )) {
            repository.reconciliationChecked(message);
            if (
                message.getStatus() == NormalizedMessageStatus.PENDING &&
                message.getCreatedAt() != null &&
                message.getCreatedAt().toInstant().isBefore(java.time.Instant.now().minusSeconds(120))
            ) {
                message = statuses.apply(
                    message,
                    NormalizedMessageStatus.UNKNOWN,
                    "PROCESS_RESTART_OR_CONFIRMATION_UNKNOWN"
                );
            }
            var tracked = logs.tracked(message);
            var identities = tracked
                .stream()
                .map(log -> log.getExternalMessageId())
                .distinct()
                .toList();
            if (message.getExternalMessageId() == null && identities.size() == 1) {
                repository.associateExternalId(message, identities.get(0), message.getSimulated());
                message.setExternalMessageId(identities.get(0));
            }
            if (message.getExternalMessageId() != null) {
                for (var callback : logs.callbacks(message)) {
                    message = statuses.apply(message, callback.getStatus(), callback.getErrorCode());
                }
            }
        }
    }

    public NormalizedMessage reconcile(
        Long channelId,
        String id,
        String externalId,
        com.wxn.chatbot.auth.service.AccountSessions.Identity actor
    ) {
        if (actor.role() != com.wxn.chatbot.user.entity.UserRole.ADMIN) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "Apenas ADMIN");
        }
        if (!org.bson.types.ObjectId.isValid(id)) {
            throw ApiException.badRequest("ID inválido");
        }
        var message = repository
            .findById(new org.bson.types.ObjectId(id))
            .orElseThrow(() -> ApiException.notFound("Mensagem não encontrada"));
        if (
            !Objects.equals(message.getChannelId(), channelId) ||
            !Set.of(NormalizedMessageStatus.UNKNOWN, NormalizedMessageStatus.PENDING).contains(
                message.getStatus()
            )
        ) {
            throw ApiException.conflict("Tentativa não elegível para reconciliação");
        }
        final Message candidate = message;
        boolean evidence = logs
            .echoes(channelId, externalId)
            .stream()
            .anyMatch(
                log ->
                    log.getEcho() != null &&
                    Objects.equals(log.getEcho().getString("phone"), candidate.getRecipientPhone()) &&
                    Objects.equals(log.getEcho().getString("text"), candidate.getContent().getString("text"))
            );
        if (!evidence) {
            throw ApiException.conflict("Nenhum callback autenticado compatível comprova este ID remoto");
        }
        repository.associateExternalId(message, externalId, message.getSimulated());
        message.setExternalMessageId(externalId);
        message = statuses.apply(message, NormalizedMessageStatus.SENT, null);
        for (var log : logs.callbacks(message)) {
            message = statuses.apply(message, log.getStatus(), log.getErrorCode());
        }
        return MessageMapper.response(message, providers.messageCapabilities(message.getProvider()));
    }
}
