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
@Profile("legacy & dev & !prod")
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

    public void status(Long channelId, NormalizedStatusEvent event) {
        var channel = channels.requireActive(channelId);
        if (!access.enabled() || channel.getProvider() != ProviderType.MOCK) {
            throw ApiException.badRequest("Simulação exige canal MOCK em dev");
        }
        messages.status(channel, event);
    }

    public void scenario(Long id, String scenario) {
        var channel = channels.requireActive(id);
        if (
            !access.enabled() ||
            channel.getProvider() != ProviderType.MOCK ||
            !java.util.Set.of("SUCCESS", "FAIL", "TIMEOUT").contains(scenario)
        ) {
            throw ApiException.badRequest("Cenário exige canal Mock local");
        }
        ((com.wxn.chatbot.whatsapp.provider.MockWhatsAppProvider) providers.resolve(channel)).scenario(
            id,
            scenario
        );
    }

    public void apiEcho(MockInboundRequest request) {
        echo(request, true);
    }

    public void outside(MockInboundRequest request) {
        echo(request, false);
    }

    private void echo(MockInboundRequest request, boolean fromApi) {
        var channel = channels.requireActive(request.channelId());
        if (!access.enabled() || channel.getProvider() != ProviderType.MOCK) {
            throw ApiException.badRequest("Canal Mock obrigatório");
        }
        var incoming = providers
            .resolve(channel)
            .normalizeInbound(
                Map.of(
                    "externalMessageId",
                    request.externalMessageId(),
                    "content",
                    request.content(),
                    "senderPhone",
                    request.senderPhone(),
                    "recipientPhone",
                    channel.getPhoneNumber()
                )
            );
        try {
            messages.echo(channel, incoming, fromApi);
        } catch (ApiException pending) {
            if (!fromApi || pending.getStatus() != org.springframework.http.HttpStatus.CONFLICT) {
                throw pending;
            }
        }
    }

    public NormalizedMessage receive(MockInboundRequest request) {
        if (!access.enabled()) {
            throw new ApiException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Acesso de desenvolvimento indisponível"
            );
        }
        var channel = channels.requireActive(request.channelId());
        if (channel.getProvider() != ProviderType.MOCK) {
            throw ApiException.badRequest("Simulação exige canal MOCK");
        }
        if (channel.getPhoneNumber() == null) {
            throw ApiException.badRequest("Configure um telefone no canal MOCK");
        }
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
        if (request.senderName() != null) {
            payload.put("senderName", request.senderName());
        }
        var normalized = providers.resolve(channel).normalizeInbound(payload);
        return messages.receive(channel, normalized, true);
    }
}
