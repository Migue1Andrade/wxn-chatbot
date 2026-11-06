package com.wxn.chatbot.whatsapp.zapi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.message.service.*;
import com.wxn.chatbot.messagelog.service.MessageLogService;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.operations.ChannelOperationsService;
import com.wxn.chatbot.whatsapp.repository.WebhookInboxRepository;
import com.wxn.chatbot.whatsapp.service.ChannelService;
import org.bson.Document;
import org.junit.jupiter.api.*;

class ZApiWebhookTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ZApiConfiguration config = mock(ZApiConfiguration.class);
    private final ChannelService channels = mock(ChannelService.class);
    private final MessageService messages = mock(MessageService.class);
    private final WebhookInboxRepository inbox = mock(WebhookInboxRepository.class);
    private final ChannelOperationsService operations = mock(ChannelOperationsService.class);
    private final WhatsAppChannel channel = mock(WhatsAppChannel.class);
    private ZApiWebhookService service;
    private final String payload =
        "{\"instanceId\":\"fixture\",\"type\":\"ReceivedCallback\",\"connectedPhone\":\"12025550190\",\"phone\":\"12025550100\",\"messageId\":\"mock-in-1\",\"momment\":1793422800000,\"text\":{\"message\":\"Olá\"}}";

    @BeforeEach
    void setup() {
        when(config.authenticated("valid-fixture")).thenReturn(true);
        when(config.channelId()).thenReturn(3L);
        when(config.instanceId()).thenReturn("fixture");
        when(channels.requireActive(3L)).thenReturn(channel);
        when(channel.getId()).thenReturn(3L);
        when(channel.getPhoneNumber()).thenReturn("+12025550190");
        when(inbox.accept(eq(3L), eq("received"), any())).thenReturn("event-key");
        service = new ZApiWebhookService(
            config,
            channels,
            messages,
            new ZApiWebhookMapper(),
            mock(MessageLogService.class),
            inbox,
            operations,
            mock(MessageMutationService.class)
        );
    }

    @Test
    void rejectsForgedCallbacksBeforePersistence() throws Exception {
        assertThatThrownBy(() -> service.receive("invalid", "received", json.readTree(payload))).isInstanceOf(
            ApiException.class
        );
        verifyNoInteractions(inbox, messages);
    }

    @Test
    void durableClaimMakesDuplicateCallbackIdempotent() throws Exception {
        when(inbox.claim("event-key")).thenReturn(
            new Document("channelId", 3L).append("kind", "received").append("payload", payload),
            null
        );
        service.receive("valid-fixture", "received", json.readTree(payload));
        service.receive("valid-fixture", "received", json.readTree(payload));
        verify(messages, times(1)).receive(eq(channel), any(), eq(false));
        verify(inbox, times(1)).finish("event-key", "APPLIED");
    }

    @Test
    void processingFailureRetainsEnvelopeForRetry() throws Exception {
        when(inbox.claim("event-key")).thenReturn(
            new Document("channelId", 3L).append("kind", "received").append("payload", payload)
        );
        when(messages.receive(eq(channel), any(), eq(false))).thenThrow(
            new IllegalStateException("fixture database failure")
        );
        service.receive("valid-fixture", "received", json.readTree(payload));
        verify(inbox).failed("event-key");
        verify(inbox, never()).finish(anyString(), anyString());
    }

    @Test
    void unsupportedMediaIsClassifiedWithoutEmptyText() throws Exception {
        String media = payload.replace(
            "\"text\":{\"message\":\"Olá\"}",
            "\"image\":{\"imageUrl\":\"https://example.com/fixture\"}"
        );
        when(inbox.claim("event-key")).thenReturn(
            new Document("channelId", 3L).append("kind", "received").append("payload", media)
        );
        service.receive("valid-fixture", "received", json.readTree(media));
        verifyNoInteractions(messages);
        verify(inbox).finish("event-key", "UNSUPPORTED_EXTERNAL_EVENT");
    }

    @Test
    void limitsPayloadBeforeJsonParsing() throws Exception {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setServletPath("/api/webhooks/zapi/received");
        request.setContent(new byte[262145]);
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var chain = mock(jakarta.servlet.FilterChain.class);
        new ZApiWebhookLimitFilter().doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(413);
        verifyNoInteractions(chain);
    }

    @Test
    void lidIsPreservedAndNeverInventsAPhone() throws Exception {
        var mapper = new ZApiWebhookMapper();
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(payload);
        body.put("senderLid", "fixture@lid");
        assertThat(mapper.text(body).externalContactIdentity()).isEqualTo("fixture@lid");
        body.put("phone", "fixture@lid");
        assertThatThrownBy(() -> mapper.text(body)).isInstanceOf(ApiException.class);
    }
}
