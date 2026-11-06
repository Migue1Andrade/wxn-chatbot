package com.wxn.chatbot.whatsapp.zapi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.wxn.chatbot.whatsapp.model.*;
import com.wxn.chatbot.whatsapp.provider.ProviderSendException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ZApiContractTest {

    private static final String BASE = "https://api.z-api.io/instances/fixture/token/fixture";
    private MockRestServiceServer server;
    private ZApiClient client;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var config = mock(ZApiConfiguration.class);
        when(config.baseUrl()).thenReturn(BASE);
        when(config.clientToken()).thenReturn("contract-fixture-client-token");
        when(config.callback(anyString())).thenAnswer(
            call -> "https://example.com/api/webhooks/zapi/" + call.getArgument(0) + "?secret=fixture-only"
        );
        client = new ZApiClient(config, builder.build());
    }

    private void expect(String path, HttpMethod method, String body, String response) {
        var expectation = server
            .expect(requestTo(BASE + path))
            .andExpect(method(method))
            .andExpect(header("Client-Token", "contract-fixture-client-token"));
        if (body != null) {
            expectation.andExpect(content().json(body, true));
        }
        expectation.andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    @Test
    void textReplyAndEditUseDistinctRemoteIdentifiers() {
        expect(
            "/send-text",
            HttpMethod.POST,
            "{\"phone\":\"12025550100\",\"message\":\"Olá\"}",
            "{\"messageId\":\"remote-1\",\"zaapId\":\"queue-1\",\"id\":\"remote-1\"}"
        );
        assertThat(client.text(new ZApiDtos.Text("12025550100", "Olá", null)).zaapId()).isEqualTo("queue-1");
        server.verify();
        server.reset();
        expect(
            "/send-text",
            HttpMethod.POST,
            "{\"phone\":\"12025550100\",\"message\":\"Resposta\",\"messageId\":\"remote-1\"}",
            "{\"messageId\":\"remote-2\"}"
        );
        client.text(new ZApiDtos.Text("12025550100", "Resposta", "remote-1"));
        server.verify();
        server.reset();
        expect(
            "/send-text",
            HttpMethod.POST,
            "{\"phone\":\"12025550100\",\"message\":\"Corrigido\",\"editMessageId\":\"remote-1\"}",
            "{\"messageId\":\"remote-1\"}"
        );
        client.mutation("EDIT_TEXT", "+12025550100", "remote-1", "Corrigido");
        server.verify();
    }

    @Test
    void forwardReactionsReadAndDeleteMatchOfficialContracts() {
        expect(
            "/forward-message",
            HttpMethod.POST,
            "{\"phone\":\"12025550101\",\"messageId\":\"remote-1\",\"messagePhone\":\"12025550100\"}",
            "{\"messageId\":\"forward-1\"}"
        );
        client.forward(new ZApiDtos.Forward("12025550101", "remote-1", "12025550100"));
        server.verify();
        server.reset();
        expect(
            "/send-reaction",
            HttpMethod.POST,
            "{\"phone\":\"12025550100\",\"reaction\":\"👍\",\"messageId\":\"remote-1\"}",
            "{\"messageId\":\"reaction-1\"}"
        );
        client.mutation("SEND_REACTION", "+12025550100", "remote-1", "👍");
        server.verify();
        server.reset();
        expect(
            "/send-remove-reaction",
            HttpMethod.POST,
            "{\"phone\":\"12025550100\",\"messageId\":\"remote-1\"}",
            "{\"messageId\":\"reaction-2\"}"
        );
        client.mutation("REMOVE_REACTION", "+12025550100", "remote-1", null);
        server.verify();
        server.reset();
        expect(
            "/read-message",
            HttpMethod.POST,
            "{\"phone\":\"12025550100\",\"messageId\":\"remote-1\"}",
            "{\"value\":true}"
        );
        client.mutation("MARK_READ", "+12025550100", "remote-1", null);
        server.verify();
        server.reset();
        expect(
            "/messages?messageId=remote-1&phone=12025550100&owner=true&deleteForMe=false",
            HttpMethod.DELETE,
            null,
            "{\"value\":true}"
        );
        client.delete(
            new DeleteMessageRequest(
                "remote-1",
                "+12025550100",
                true,
                DeleteMessageRequest.DeleteIntent.DELETE_FOR_EVERYONE
            )
        );
        server.verify();
    }

    @Test
    void statusQrAndSensitiveOperationsNeverUseBrowserEndpoints() {
        expect(
            "/status",
            HttpMethod.GET,
            null,
            "{\"connected\":false,\"smartphoneConnected\":true,\"error\":\"disconnected\"}"
        );
        assertThat(client.connection().connected()).isFalse();
        server.verify();
        server.reset();
        expect("/qr-code/image", HttpMethod.GET, null, "{\"challenge\":{\"challenge\":\"fixture\"}}");
        assertThat(client.qr().has("challenge")).isTrue();
        server.verify();
        server.reset();
        expect("/restart", HttpMethod.GET, null, "{\"value\":true}");
        client.instanceOperation("restart");
        server.verify();
        server.reset();
        expect("/disconnect", HttpMethod.GET, null, "{\"value\":true}");
        client.instanceOperation("disconnect");
        server.verify();
    }

    @Test
    void webhookRegistrationUsesOfficialPutContracts() {
        for (var pair : List.of(
            new String[] { "update-webhook-received", "received" },
            new String[] { "update-webhook-received-delivery", "received" },
            new String[] { "update-webhook-message-status", "message-status" },
            new String[] { "update-webhook-delivery", "sent" },
            new String[] { "update-webhook-connected", "connected" },
            new String[] { "update-webhook-disconnected", "disconnected" }
        )) {
            expect(
                "/" + pair[0],
                HttpMethod.PUT,
                "{\"value\":\"https://example.com/api/webhooks/zapi/" + pair[1] + "?secret=fixture-only\"}",
                "{\"value\":true}"
            );
        }
        expect("/update-notify-sent-by-me", HttpMethod.PUT, "{\"notifySentByMe\":true}", "{\"value\":true}");
        client.registerWebhooks();
        server.verify();
    }

    @Test
    void errorsAndMalformedResponsesAreSanitizedWithoutRetries() {
        server
            .expect(requestTo(BASE + "/status"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("secret-should-not-escape"));
        assertThatThrownBy(client::connection)
            .isInstanceOf(ProviderSendException.class)
            .hasMessage("Falha no envio ao provedor")
            .satisfies(error -> assertThat(((ProviderSendException) error).code()).isEqualTo("ZAPI_HTTP_401"))
            .hasNoCause();
        server.verify();
    }

    @Test
    void invalidJsonIsUncertainRatherThanConfirmed() {
        expect("/send-text", HttpMethod.POST, "{\"phone\":\"12025550100\",\"message\":\"Olá\"}", "not-json");
        assertThatThrownBy(() -> client.text(new ZApiDtos.Text("12025550100", "Olá", null))).satisfies(
            error -> assertThat(((ProviderSendException) error).code()).isEqualTo("ZAPI_CONFIRMATION_UNKNOWN")
        );
        server.verify();
    }

    @Test
    void configRejectsArbitraryHostsAndHttp() {
        for (String url : List.of(
            "http://localhost/instances/a/token/b",
            "https://evil.example/instances/a/token/b",
            "https://api.z-api.io:8443/instances/a/token/b"
        )) {
            var config = new ZApiConfiguration(
                url,
                "fixture",
                1L,
                "fixture-secret-minimum-length-32-chars",
                "https://example.com"
            );
            assertThatThrownBy(config::requireConfigured).hasMessage(
                "Configuração segura Z-API incompleta ou inválida"
            );
        }
    }

    @Test
    void transportTimeoutIsUnknownAndIsNotRetried() {
        server.expect(requestTo(BASE + "/send-text")).andRespond(request -> {
            throw new java.net.SocketTimeoutException("fixture-sensitive-url");
        });
        assertThatThrownBy(() -> client.text(new ZApiDtos.Text("12025550100", "Olá", null)))
            .satisfies(error ->
                assertThat(((ProviderSendException) error).code()).isEqualTo("ZAPI_CONFIRMATION_UNKNOWN")
            )
            .hasNoCause();
        server.verify();
    }

    @Test
    void oversizedProviderResponseIsNeverAccepted() {
        expect("/status", HttpMethod.GET, null, "{\"padding\":\"" + "a".repeat(1048576) + "\"}");
        assertThatThrownBy(client::connection).satisfies(error ->
            assertThat(((ProviderSendException) error).code()).isEqualTo("ZAPI_CONFIRMATION_UNKNOWN")
        );
        server.verify();
    }

    @Test
    void structuredLogsNeverContainCredentialsOrProviderBody() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ZApiClient.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            server
                .expect(requestTo(BASE + "/status"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("fixture-sensitive-provider-body"));
            assertThatThrownBy(client::connection).isInstanceOf(ProviderSendException.class);
            String logs = appender.list
                .stream()
                .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .collect(java.util.stream.Collectors.joining("\n"));
            assertThat(logs)
                .contains("operation=status")
                .doesNotContain(BASE, "contract-fixture-client-token", "fixture-sensitive-provider-body");
            server.verify();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
