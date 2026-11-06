package com.wxn.chatbot.whatsapp.operations;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.user.entity.UserRole;
import com.wxn.chatbot.whatsapp.model.*;
import com.wxn.chatbot.whatsapp.provider.*;
import com.wxn.chatbot.whatsapp.service.ChannelService;
import java.util.*;
import org.bson.Document;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class ChannelOperationsService {

    public record Changed(Long channelId, String state) {}

    public record Diagnostic(
        Long channelId,
        ProviderType provider,
        ChannelConnection connection,
        Set<MessageCapability> capabilities,
        long conversations,
        long unknown,
        long failed,
        long pending,
        Object lastCallbackAt,
        String lastCallbackKind,
        boolean remoteReadEnabled,
        String webhookStatus,
        String lastErrorCode,
        List<Document> unresolved,
        List<Document> pendingMutations
    ) {}

    private final com.wxn.chatbot.whatsapp.repository.WebhookInboxRepository inbox;
    private final ChannelService channels;
    private final WhatsAppProviderResolver providers;
    private final ChannelOperationsRepository store;
    private final ApplicationEventPublisher events;

    public ChannelOperationsService(
        ChannelService channels,
        WhatsAppProviderResolver providers,
        ChannelOperationsRepository store,
        ApplicationEventPublisher events,
        com.wxn.chatbot.whatsapp.repository.WebhookInboxRepository inbox
    ) {
        this.inbox = inbox;
        this.channels = channels;
        this.providers = providers;
        this.store = store;
        this.events = events;
    }

    public Diagnostic diagnostic(Long id) {
        var channel = channels.require(id);
        var adapter = providers.resolve(channel);
        var runtime = store.runtime(id);
        Document error = store.latestError(id);
        return new Diagnostic(
            id,
            channel.getProvider(),
            adapter.connection(channel),
            adapter.messageCapabilities(),
            store.count("conversations", new Document("channelId", id)),
            count(id, "UNKNOWN"),
            count(id, "FAILED"),
            count(id, "PENDING"),
            runtime.get("lastCallbackAt"),
            runtime.getString("lastCallbackKind"),
            Boolean.TRUE.equals(runtime.getBoolean("remoteReadEnabled")),
            runtime.get("lastCallbackAt") == null ? "NOT_VERIFIED" : "CALLBACK_RECEIVED",
            error == null ? null : error.getString("errorCode"),
            store.unresolved(id),
            store.pendingMutations(id)
        );
    }

    public java.util.List<org.bson.Document> webhookFailures(Long id) {
        channels.require(id);
        return inbox.failures(id);
    }

    public void retryWebhook(Long id, String key) {
        channels.require(id);
        inbox.retry(id, key);
    }

    private long count(Long id, String status) {
        return store.count("messages", new Document("channelId", id).append("status", status));
    }

    public ConnectionInstructions qr(Long id) {
        return providers.resolve(channels.requireActive(id)).instructions(channels.require(id));
    }

    public void webhooks(Long id, String key, String confirmation, AccountSessions.Identity actor) {
        admin(actor);
        if (!"CONFIRM".equals(confirmation)) {
            throw ApiException.badRequest("Confirmação obrigatória");
        }
        var channel = channels.requireActive(id);
        var adapter = providers.resolve(channel);
        if (!adapter.messageCapabilities().contains(MessageCapability.CONFIGURE_WEBHOOKS)) {
            throw ApiException.conflict("Provider não configura callbacks");
        }
        if (store.audit(id, actor.id(), "webhooks", key) != null) {
            throw ApiException.conflict("Configuração já solicitada; consulte o diagnóstico");
        }
        try {
            adapter.configureWebhooks(channel);
            store.outcome(id, key, "ACCEPTED");
        } catch (RuntimeException failed) {
            store.outcome(id, key, "PARTIAL_OR_UNKNOWN");
            throw failed;
        }
    }

    public void remoteRead(Long id, boolean enabled, AccountSessions.Identity actor) {
        admin(actor);
        channels.require(id);
        store.remoteRead(id, enabled);
    }

    public void operation(
        Long id,
        String operation,
        String key,
        String confirmation,
        AccountSessions.Identity actor
    ) {
        admin(actor);
        if (!"CONFIRM".equals(confirmation) || !Set.of("restart", "disconnect").contains(operation)) {
            throw ApiException.badRequest("Confirmação explícita e operação válida obrigatórias");
        }
        var channel = channels.requireActive(id);
        var adapter = providers.resolve(channel);
        var capability = "restart".equals(operation)
            ? MessageCapability.RESTART_INSTANCE
            : MessageCapability.DISCONNECT_INSTANCE;
        if (!adapter.messageCapabilities().contains(capability)) {
            throw ApiException.conflict("Operação não suportada");
        }
        var existing = store.audit(id, actor.id(), operation, key);
        if (existing != null) {
            throw ApiException.conflict("Operação já registrada; consulte o diagnóstico antes de repetir");
        }
        try {
            adapter.instanceOperation(channel, operation);
            store.outcome(id, key, "ACCEPTED");
        } catch (RuntimeException error) {
            store.outcome(
                id,
                key,
                error instanceof ProviderSendException failure ? failure.code() : "PROVIDER_OPERATION_FAILED"
            );
            throw ApiException.conflict("Não foi possível confirmar a operação. Consulte o diagnóstico.");
        }
        events.publishEvent(new Changed(id, "UNKNOWN"));
    }

    public void observed(Long id, String state, String kind) {
        store.observe(id, state, kind);
        if (state != null) {
            events.publishEvent(new Changed(id, state));
        }
    }

    private void admin(AccountSessions.Identity actor) {
        if (actor.role() != UserRole.ADMIN) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "Apenas ADMIN");
        }
    }
}
