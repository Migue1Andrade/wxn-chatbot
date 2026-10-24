package com.wxn.chatbot.whatsapp.provider;

import com.wxn.chatbot.whatsapp.model.NormalizedStatusEvent;
import java.util.Map;

public interface WhatsAppStatusNormalizer {
    NormalizedStatusEvent normalizeStatus(Map<String, String> payload);
}
