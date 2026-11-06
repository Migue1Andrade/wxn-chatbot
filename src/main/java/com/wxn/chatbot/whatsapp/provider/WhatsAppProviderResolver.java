package com.wxn.chatbot.whatsapp.provider;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppProviderResolver {

    private final Map<ProviderType, WhatsAppProvider> providers = new EnumMap<>(ProviderType.class);

    public WhatsAppProviderResolver(List<WhatsAppProvider> adapters) {
        for (WhatsAppProvider adapter : adapters) {
            if (providers.put(adapter.type(), adapter) != null) {
                throw new IllegalStateException("Adapter duplicado");
            }
        }
    }

    public java.util.Set<MessageCapability> messageCapabilities(ProviderType type) {
        var adapter = providers.get(type);
        return adapter == null ? java.util.Set.of() : adapter.messageCapabilities();
    }

    public WhatsAppProvider resolve(WhatsAppChannel channel) {
        WhatsAppProvider provider = providers.get(channel.getProvider());
        if (provider == null) {
            throw ApiException.conflict("Provedor não disponível neste ambiente");
        }
        return provider;
    }

    public WhatsAppProvider forSend(
        WhatsAppChannel channel,
        ConversationType conversationType,
        MessageType messageType
    ) {
        WhatsAppProvider provider = resolve(channel);
        ProviderCapabilities capabilities = provider.capabilities();
        if (conversationType == ConversationType.GROUP && !capabilities.groups()) {
            throw ApiException.badRequest("Provedor não suporta grupos");
        }
        if (messageType != MessageType.TEXT || !capabilities.individualText()) {
            throw ApiException.badRequest("Envio suportado somente para texto individual");
        }
        return provider;
    }
}
