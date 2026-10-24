package com.wxn.chatbot.whatsapp.service;

import com.twilio.security.RequestValidator;
import com.wxn.chatbot.common.*;
import com.wxn.chatbot.message.service.MessageService;
import com.wxn.chatbot.whatsapp.config.TwilioProperties;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.provider.TwilioWhatsAppProvider;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TwilioIngressService {

    private final TwilioProperties config;
    private final ChannelService channels;
    private final TwilioWhatsAppProvider provider;
    private final MessageService messages;

    public TwilioIngressService(
        TwilioProperties config,
        ChannelService channels,
        TwilioWhatsAppProvider provider,
        MessageService messages
    ) {
        this.config = config;
        this.channels = channels;
        this.provider = provider;
        this.messages = messages;
    }

    public void receive(String channelKey, String signature, Map<String, String> form, boolean status) {
        config.requireConfigured();
        if (!config.getChannelKey().equals(channelKey)) throw ApiException.notFound("Webhook não encontrado");
        String url = status ? config.statusUrl() : config.inboundUrl();
        // Use the configured public URL, never untrusted forwarded headers/Host.
        if (
            signature == null ||
            !new RequestValidator(config.getAuthToken()).validate(url, form, signature) ||
            !config.getAccountSid().equals(form.get("AccountSid"))
        ) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Assinatura ou conta Twilio inválida");
        }
        WhatsAppChannel channel = channels.requireActive(config.getCompanyId(), config.getChannelId());
        provider.validateChannel(channel);
        if (status) {
            if (
                form.containsKey("From") &&
                !channel.getPhoneNumber().equals(PhoneNumbers.required(form.get("From")))
            ) {
                throw ApiException.badRequest("Remetente do callback não corresponde ao canal");
            }
            messages.status(channel, provider.normalizeStatus(form));
        } else messages.receive(channel, provider.normalizeInbound(form), false);
    }
}
