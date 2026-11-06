package com.wxn.chatbot.whatsapp.zapi;

import com.wxn.chatbot.whatsapp.service.ChannelService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("legacy")
public class ZApiAdministrationService {

    private final com.wxn.chatbot.whatsapp.operations.ChannelOperationsRepository audit;
    private final ZApiConfiguration config;
    private final ZApiClient client;
    private final ChannelService channels;

    public ZApiAdministrationService(
        ZApiConfiguration config,
        ZApiClient client,
        ChannelService channels,
        com.wxn.chatbot.whatsapp.operations.ChannelOperationsRepository audit
    ) {
        this.audit = audit;
        this.config = config;
        this.client = client;
        this.channels = channels;
    }

    public ZApiDtos.Connection connection(Long id) {
        config.requireChannel(channels.requireActive(id));
        var result = client.connection();
        return new ZApiDtos.Connection(
            result.connected(),
            result.smartphoneConnected(),
            Boolean.TRUE.equals(result.connected()) ? "REMOTE_CONNECTED" : "REMOTE_NOT_CONNECTED"
        );
    }

    public void register(Long id, Long actor) {
        config.requireChannel(channels.requireActive(id));
        String key = java.util.UUID.randomUUID().toString();
        audit.audit(id, actor, "webhooks", key);
        try {
            client.registerWebhooks();
            audit.outcome(id, key, "ACCEPTED");
        } catch (RuntimeException failure) {
            audit.outcome(id, key, "PARTIAL_OR_UNKNOWN");
            throw failure;
        }
    }
}
