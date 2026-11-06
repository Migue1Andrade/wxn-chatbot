package com.wxn.chatbot.whatsapp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.message.service.MessageService;
// Signed fixture values are independent of the SDK validator under test.
import com.wxn.chatbot.whatsapp.config.TwilioProperties;
import com.wxn.chatbot.whatsapp.provider.TwilioWhatsAppProvider;
import com.wxn.chatbot.whatsapp.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TwilioIngressTest {

    private TwilioProperties config() {
        var c = new TwilioProperties();

        c.setChannelId(20L);
        c.setChannelKey("sandbox-test");
        c.setAccountSid("ACtest");
        c.setAuthToken("test-token-for-unit-tests");
        c.setPhoneNumber("+14155238886");
        c.setPublicBaseUrl("https://test.example");
        return c;
    }

    @Test
    void invalidSignatureAndAccountNeverResolveChannelOrPersist() {
        var config = config();
        var channels = mock(ChannelService.class);
        var messages = mock(MessageService.class);
        var service = new TwilioIngressService(
            config,
            channels,
            new TwilioWhatsAppProvider(config),
            messages
        );
        Map<String, String> form = Map.of("AccountSid", "ACother", "MessageSid", "SMtest");
        assertThatThrownBy(() -> service.receive("sandbox-test", "invalid", form, false)).isInstanceOf(
            ApiException.class
        );
        String signed = "49re+epBebgwM6mr1434CBhAExo=";
        assertThatThrownBy(() -> service.receive("sandbox-test", signed, form, false)).isInstanceOf(
            ApiException.class
        );
        assertThatThrownBy(() -> service.receive("other-key", signed, form, false)).isInstanceOf(
            ApiException.class
        );
        verifyNoInteractions(channels, messages);
    }

    @Test
    void signedWebhookResolvesOnlyConfiguredChannel() {
        var config = config();
        var channels = mock(ChannelService.class);
        var messages = mock(MessageService.class);
        var channel = mock(com.wxn.chatbot.whatsapp.entity.WhatsAppChannel.class);
        when(channel.getId()).thenReturn(20L);

        when(channel.getProvider()).thenReturn(com.wxn.chatbot.whatsapp.model.ProviderType.TWILIO);
        when(channel.getPhoneNumber()).thenReturn("+14155238886");
        when(channels.requireActive(20L)).thenReturn(channel);
        var service = new TwilioIngressService(
            config,
            channels,
            new TwilioWhatsAppProvider(config),
            messages
        );
        var form = Map.of(
            "AccountSid",
            "ACtest",
            "MessageSid",
            "SMtest",
            "From",
            "whatsapp:+5581999999999",
            "To",
            "whatsapp:+14155238886",
            "Body",
            "Olá"
        );
        var signature = "VeuiXDa8TbHOJmncYeHv9FLAhqE=";
        service.receive("sandbox-test", signature, form, false);
        verify(channels).requireActive(20L);
        verify(messages).receive(eq(channel), any(), eq(false));
        assertThatThrownBy(() -> service.receive("sandbox-test", signature, form, true)).isInstanceOf(
            ApiException.class
        );
    }
}
