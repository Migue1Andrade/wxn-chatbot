package com.wxn.chatbot.whatsapp.provider;

import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.*;
import java.util.Map;

public interface WhatsAppProvider {
    ProviderType type();
    ProviderCapabilities capabilities();

    default void validateChannel(WhatsAppChannel channel) {
        // Providers without external configuration need no additional local validation.
    }

    SendMessageResult send(WhatsAppChannel channel, SendMessageRequest request);

    default java.util.Set<MessageCapability> messageCapabilities() {
        return java.util.Set.of(MessageCapability.SEND_TEXT);
    }

    default SendMessageResult reply(
        WhatsAppChannel channel,
        SendMessageRequest request,
        String externalMessageId
    ) {
        throw com.wxn.chatbot.common.ApiException.conflict("Provider não suporta resposta citada");
    }

    default SendMessageResult forward(WhatsAppChannel channel, ForwardMessageRequest request) {
        throw com.wxn.chatbot.common.ApiException.conflict("Provider não suporta encaminhamento");
    }

    default void delete(WhatsAppChannel channel, DeleteMessageRequest request) {
        throw com.wxn.chatbot.common.ApiException.conflict("Provider não suporta exclusão remota");
    }

    default ChannelConnection connection(WhatsAppChannel channel) {
        return ChannelConnection.of("UNKNOWN", false, "CAPABILITY_NOT_SUPPORTED");
    }

    default ConnectionInstructions instructions(WhatsAppChannel channel) {
        throw com.wxn.chatbot.common.ApiException.conflict("Pareamento não suportado pelo provider");
    }

    default void configureWebhooks(WhatsAppChannel channel) {
        throw com.wxn.chatbot.common.ApiException.conflict("Configuração de callbacks não suportada");
    }

    default void instanceOperation(WhatsAppChannel channel, String operation) {
        throw com.wxn.chatbot.common.ApiException.conflict("Operação administrativa não suportada");
    }

    /** Returns CONFIRMED only for a synchronous, authoritative acknowledgement. */
    default String mutate(
        WhatsAppChannel channel,
        String operation,
        String phone,
        String externalId,
        String value
    ) {
        throw com.wxn.chatbot.common.ApiException.conflict("Operação não suportada pelo provider");
    }

    NormalizedInboundMessage normalizeInbound(Map<String, String> payload);
}
