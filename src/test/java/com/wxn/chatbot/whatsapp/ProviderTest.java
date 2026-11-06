package com.wxn.chatbot.whatsapp;

import static org.assertj.core.api.Assertions.*;

import com.wxn.chatbot.common.*;
import com.wxn.chatbot.whatsapp.config.TwilioProperties;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.*;
import com.wxn.chatbot.whatsapp.provider.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProviderTest {

    private WhatsAppChannel channel(ProviderType provider) {
        var channel = new WhatsAppChannel("Canal", provider, "+12025550190", null, ActiveStatus.ACTIVE);
        org.springframework.test.util.ReflectionTestUtils.setField(channel, "id", 2L);
        return channel;
    }

    @Test
    void selectsSimultaneousAdaptersByChannelAndRejectsUnavailableProviders() {
        var mock = new MockWhatsAppProvider();
        var twilio = new TwilioWhatsAppProvider(new TwilioProperties());
        var resolver = new WhatsAppProviderResolver(List.of(mock, twilio));
        assertThat(resolver.resolve(channel(ProviderType.MOCK))).isSameAs(mock);
        assertThat(resolver.resolve(channel(ProviderType.TWILIO))).isSameAs(twilio);
        assertThatThrownBy(() ->
            new WhatsAppProviderResolver(List.of(twilio)).resolve(channel(ProviderType.MOCK))
        ).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsUnsupportedGroupsAndMedia() {
        var resolver = new WhatsAppProviderResolver(
            List.of(new TwilioWhatsAppProvider(new TwilioProperties()))
        );
        assertThatThrownBy(() ->
            resolver.forSend(channel(ProviderType.TWILIO), ConversationType.GROUP, MessageType.TEXT)
        ).isInstanceOf(ApiException.class);
        assertThatThrownBy(() ->
            resolver.forSend(channel(ProviderType.TWILIO), ConversationType.INDIVIDUAL, MessageType.MEDIA)
        ).isInstanceOf(ApiException.class);
    }

    @Test
    void mockIdsAreStableAndClearlySimulated() {
        var mock = new MockWhatsAppProvider();
        var request = new SendMessageRequest(
            "abc",
            2L,
            "+5581999999999",
            "Olá",
            ConversationType.INDIVIDUAL,
            MessageType.TEXT
        );
        var a = mock.send(channel(ProviderType.MOCK), request);
        assertThat(a).isEqualTo(mock.send(channel(ProviderType.MOCK), request));
        assertThat(a.status()).isEqualTo(NormalizedMessageStatus.SIMULATED);
        assertThat(a.simulated()).isTrue();
    }

    @Test
    void bothAdaptersNormalizeTextAndPhoneNumbers() {
        var mock = new MockWhatsAppProvider();
        var twilio = new TwilioWhatsAppProvider(new TwilioProperties());
        var a = mock.normalizeInbound(
            Map.of(
                "externalMessageId",
                "one",
                "senderPhone",
                "+55 (81) 99999-9999",
                "recipientPhone",
                "+14155238886",
                "content",
                "Olá"
            )
        );
        var b = twilio.normalizeInbound(
            Map.of(
                "MessageSid",
                "SMone",
                "From",
                "whatsapp:+5581999999999",
                "To",
                "whatsapp:+14155238886",
                "Body",
                "Olá"
            )
        );
        assertThat(a.senderPhone()).isEqualTo(b.senderPhone());
        assertThat(a.content()).isEqualTo(b.content());
        assertThat(a.messageType()).isEqualTo(MessageType.TEXT);
        assertThatThrownBy(() -> PhoneNumbers.required("81999999999")).isInstanceOf(ApiException.class);
        assertThat(PhoneNumbers.optional(null)).isNull();
    }

    @Test
    void twilioNeverEquatesAcceptanceToDelivery() {
        var twilio = new TwilioWhatsAppProvider(new TwilioProperties());
        assertThat(twilio.status("queued")).isEqualTo(NormalizedMessageStatus.ACCEPTED);
        assertThat(twilio.status("sent")).isEqualTo(NormalizedMessageStatus.SENT);
        assertThat(twilio.status("delivered")).isEqualTo(NormalizedMessageStatus.DELIVERED);
        assertThat(twilio.status("read")).isEqualTo(NormalizedMessageStatus.READ);
        assertThat(twilio.status("undelivered")).isEqualTo(NormalizedMessageStatus.FAILED);
        assertThat(twilio.status("future-status")).isEqualTo(NormalizedMessageStatus.UNKNOWN);
    }

    @Test
    void statusTransitionsRejectDuplicatesAndRegression() {
        assertThat(
            com.wxn.chatbot.message.service.MessageService.allowedPrevious(NormalizedMessageStatus.SENT)
        ).doesNotContain(
            NormalizedMessageStatus.DELIVERED,
            NormalizedMessageStatus.READ,
            NormalizedMessageStatus.FAILED,
            NormalizedMessageStatus.SENT
        );
        assertThat(
            com.wxn.chatbot.message.service.MessageService.allowedPrevious(NormalizedMessageStatus.FAILED)
        ).doesNotContain(NormalizedMessageStatus.DELIVERED, NormalizedMessageStatus.READ);
    }
}
