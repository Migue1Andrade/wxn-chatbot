package com.wxn.chatbot.whatsapp.evolution;

import com.fasterxml.jackson.databind.JsonNode;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.whatsapp.dto.ChannelDtos;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.ChannelConnection;
import com.wxn.chatbot.whatsapp.model.ConnectionInstructions;
import com.wxn.chatbot.whatsapp.model.ProviderType;
import com.wxn.chatbot.whatsapp.service.ChannelService;
import com.wxn.chatbot.whatsapp.provider.ProviderSendException;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvolutionSetupService {

    public record Status(
        boolean configured,
        boolean completed,
        String state,
        String qrCode,
        String connectedPhone,
        Long channelId,
        String message
    ) {}

    private static final int SETUP_ID = 1;
    private final EvolutionConfiguration config;
    private final EvolutionApiClient client;
    private final EvolutionWhatsAppProvider provider;
    private final WhatsAppSetupRepository setupRepository;
    private final ChannelService channels;

    public EvolutionSetupService(
        EvolutionConfiguration config,
        EvolutionApiClient client,
        EvolutionWhatsAppProvider provider,
        WhatsAppSetupRepository setupRepository,
        ChannelService channels
    ) {
        this.config = config;
        this.client = client;
        this.provider = provider;
        this.setupRepository = setupRepository;
        this.channels = channels;
    }

    @Transactional(readOnly = true)
    public Status status() {
        var saved = setupRepository.findById(SETUP_ID).orElse(null);
        if (!config.configured()) {
            return new Status(
                false,
                saved != null && saved.isCompleted(),
                "NOT_CONFIGURED",
                null,
                saved == null ? null : saved.getConnectedPhone(),
                saved == null ? null : saved.getChannelId(),
                "Configure as variáveis da Evolution API no backend."
            );
        }
        WhatsAppChannel channel = channels.firstActive(ProviderType.EVOLUTION);
        if (channel == null) {
            return new Status(
                true,
                false,
                "NOT_INITIALIZED",
                null,
                null,
                null,
                "A instância ainda não foi inicializada."
            );
        }
        ChannelConnection connection = provider.connection(channel);
        boolean completed = saved != null && saved.isCompleted();
        return new Status(
            true,
            completed,
            connection.state(),
            null,
            saved == null ? channel.getPhoneNumber() : saved.getConnectedPhone(),
            channel.getId(),
            connection.errorCode() == null ? null : "Evolution indisponível no momento."
        );
    }

    @Transactional
    public Status initialize(String token) {
        authorize(token);
        config.requireConfigured();
        WhatsAppChannel channel = channels.firstActive(ProviderType.EVOLUTION);
        if (channel == null) {
            channel = channels.require(
                channels
                    .create(
                        new ChannelDtos.Create(
                            "WhatsApp Evolution",
                            ProviderType.EVOLUTION,
                            null,
                            config.instanceName(),
                            com.wxn.chatbot.common.ActiveStatus.ACTIVE
                        )
                    )
                    .id()
            );
        }
        var saved = setupRepository
            .findById(SETUP_ID)
            .orElseGet(() -> setupRepository.save(new WhatsAppSetup(SETUP_ID)));
        saved.setChannelId(channel.getId());
        setupRepository.save(saved);
        if (!instanceExists()) {
            client.createInstance();
        }
        client.configureWebhook();
        ConnectionInstructions instructions = provider.instructions(channel);
        return new Status(
            true,
            saved.isCompleted(),
            instructions.state(),
            instructions.image(),
            saved.getConnectedPhone(),
            channel.getId(),
            instructions.instruction()
        );
    }

    @Transactional
    public Status refresh(String token) {
        authorize(token);
        Status current = status();
        if (!current.configured() || current.channelId() == null) {
            return current;
        }
        WhatsAppChannel channel = channels.requireActive(current.channelId());
        ChannelConnection connection = provider.connection(channel);
        if (connection.available()) {
            complete(channel, channel.getPhoneNumber());
        }
        var saved = setupRepository.findById(SETUP_ID).orElse(null);
        return new Status(
            true,
            saved != null && saved.isCompleted(),
            connection.state(),
            null,
            saved == null ? channel.getPhoneNumber() : saved.getConnectedPhone(),
            channel.getId(),
            connection.errorCode() == null ? null : "Evolution indisponível no momento."
        );
    }

    @Transactional
    public void observe(JsonNode body, String secret) {
        if (!config.configured() || secret == null || !config.webhookSecret().equals(secret)) {
            return;
        }
        String event = body.path("event").asText("");
        if (!"CONNECTION_UPDATE".equals(event)) {
            return;
        }
        String state = body.path("data").path("state").asText(body.path("data").path("status").asText(""));
        if (!"open".equalsIgnoreCase(state)) {
            return;
        }
        WhatsAppChannel channel = channels.firstActive(ProviderType.EVOLUTION);
        if (channel != null) {
            String phone = phone(body);
            complete(channel, phone == null ? channel.getPhoneNumber() : phone);
        }
    }

    private void complete(WhatsAppChannel channel, String phone) {
        if (phone != null && !phone.isBlank()) {
            channels.updatePhone(channel, phone);
        }
        var saved = setupRepository.findById(SETUP_ID).orElseGet(() -> new WhatsAppSetup(SETUP_ID));
        saved.setChannelId(channel.getId());
        saved.complete(phone);
        setupRepository.save(saved);
    }

    private boolean instanceExists() {
        try {
            JsonNode result = client.instances();
            return result.isArray() ? !result.isEmpty() : result.path("instance").isObject();
        } catch (ProviderSendException error) {
            if ("EVOLUTION_HTTP_404".equals(error.code())) {
                return false;
            }
            throw error;
        }
    }

    private void authorize(String token) {
        if (!config.bootstrapConfigured() || !config.validBootstrapToken(token)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Token de bootstrap inválido");
        }
        var saved = setupRepository.findById(SETUP_ID).orElse(null);
        if (saved != null && saved.isCompleted()) {
            throw new ApiException(HttpStatus.CONFLICT, "Configuração inicial já concluída");
        }
    }

    private String phone(JsonNode body) {
        String owner = body.path("data").path("instance").path("owner").asText(null);
        if (owner == null) {
            owner = body.path("data").path("owner").asText(null);
        }
        if (owner == null || !owner.contains("@")) {
            return owner;
        }
        String digits = owner.substring(0, owner.indexOf('@'));
        return com.wxn.chatbot.common.PhoneNumbers.required(digits.startsWith("+") ? digits : "+" + digits);
    }
}
