package com.wxn.chatbot.whatsapp.service;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.config.DevelopmentAccess;
import com.wxn.chatbot.message.service.MessageService;
import com.wxn.chatbot.whatsapp.dto.MockInboundRequest;
import com.wxn.chatbot.whatsapp.model.*;
import com.wxn.chatbot.whatsapp.provider.WhatsAppProviderResolver;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev & !prod")
public class MockIngressService {

    private final DevelopmentAccess access;
    private final ChannelService channels;
    private final WhatsAppProviderResolver providers;
    private final MessageService messages;

    public MockIngressService(
        ChannelService channels,
        WhatsAppProviderResolver providers,
        MessageService messages,
        DevelopmentAccess access
    ) {
        this.access = access;
        this.channels = channels;
        this.providers = providers;
        this.messages = messages;
    }

    public NormalizedMessage receive(MockInboundRequest request) {
        if (!access.allows(request.companyId())) throw new ApiException(
            org.springframework.http.HttpStatus.FORBIDDEN,
            "Empresa não permitida no contexto de desenvolvimento"
        );
        var channel = channels.requireActive(request.companyId(), request.channelId());
        if (channel.getProvider() != ProviderType.MOCK) throw ApiException.badRequest(
            "Simulação exige canal MOCK"
        );
        if (channel.getPhoneNumber() == null) throw ApiException.badRequest(
            "Configure um telefone no canal MOCK"
        );
        Map<String, String> payload = new java.util.HashMap<>(
            Map.of(
                "externalMessageId",
                request.externalMessageId(),
                "senderPhone",
                request.senderPhone(),
                "recipientPhone",
                channel.getPhoneNumber(),
                "content",
                request.content()
            )
        );
        if (request.senderName() != null) payload.put("senderName", request.senderName());
        var normalized = providers.resolve(channel).normalizeInbound(payload);
        return messages.receive(channel, normalized, true);
    }
}
