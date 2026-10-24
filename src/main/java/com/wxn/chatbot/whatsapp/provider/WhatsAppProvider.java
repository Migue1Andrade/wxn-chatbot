package com.wxn.chatbot.whatsapp.provider;

import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.*;
import java.util.Map;

public interface WhatsAppProvider {
    ProviderType type();
    ProviderCapabilities capabilities();
    SendMessageResult send(WhatsAppChannel channel, SendMessageRequest request);
    NormalizedInboundMessage normalizeInbound(Map<String, String> payload);
}
