package com.wxn.chatbot.bot.client;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.wxn.chatbot.bot.config.BotConfiguration;
import com.wxn.chatbot.bot.dto.BotDtos;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BotClientTest {

    private static final String API_KEY = "test-key-with-at-least-32-characters";
    private static final String REPLY_URL = "http://bot:8000/reply";

    private MockRestServiceServer server;
    private BotClient client;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var config = new BotConfiguration(true, "http://bot:8000", API_KEY, 7L, 45);
        client = new BotClient(config, builder.build());
    }

    @Test
    void sendsContractInCamelCaseWithApiKeyAndReadsDecision() {
        server
            .expect(requestTo(REPLY_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Api-Key", API_KEY))
            .andExpect(
                content().json(
                    """
                    {"conversationId":"c-1","channelId":1,"inboundMessageId":"m-1","message":"Olá",
                     "history":[{"direction":"INBOUND","text":"Oi","sentAt":"2026-11-21T10:00:00Z"}],
                     "departments":[{"id":3,"name":"Financeiro","description":"Boletos"}]}
                    """,
                    true
                )
            )
            .andRespond(
                withSuccess(
                    """
                    {"reply":"Olá!","intent":"saudacao","needsHuman":false,"departmentId":null,
                     "handoffReason":null,"run":{"model":"claude-haiku-5-5","inputTokens":1000,
                     "outputTokens":80,"cachedInputTokens":0,"cacheWriteInputTokens":900,
                     "historyMessages":1,"latencyMs":1900,
                     "requestId":"req_test123","fallbackStage":"secondary",
                     "guardrails":["sensitive_data"],"failures":["primary:sensitive_data"],
                     "toolCalls":["buscar_conhecimento"],"retrieved":["pagamentos.md | Pagamentos"]}}
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        var response = client.reply(request());

        server.verify();
        assertThat(response.reply()).isEqualTo("Olá!");
        assertThat(response.needsHuman()).isFalse();
        assertThat(response.run().model()).isEqualTo("claude-haiku-5-5");
        assertThat(response.run().latencyMs()).isEqualTo(1900);
        assertThat(response.run().requestId()).isEqualTo("req_test123");
        assertThat(response.run().cacheWriteInputTokens()).isEqualTo(900);
        assertThat(response.run().historyMessages()).isEqualTo(1);
        assertThat(response.run().fallbackStage()).isEqualTo("secondary");
        assertThat(response.run().guardrails()).containsExactly("sensitive_data");
        assertThat(response.run().toolCalls()).containsExactly("buscar_conhecimento");
    }

    @Test
    void httpErrorBecomesUnavailableWithStatusCode() {
        server.expect(requestTo(REPLY_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.reply(request()))
            .isInstanceOf(BotUnavailableException.class)
            .extracting(error -> ((BotUnavailableException) error).code())
            .isEqualTo("BOT_HTTP_502");
    }

    @Test
    void connectionFailureBecomesUnavailable() {
        server.expect(requestTo(REPLY_URL)).andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> client.reply(request()))
            .isInstanceOf(BotUnavailableException.class)
            .extracting(error -> ((BotUnavailableException) error).code())
            .isEqualTo("BOT_UNAVAILABLE");
    }

    @Test
    void enabledBotRequiresCompleteConfiguration() {
        assertThatThrownBy(() -> new BotConfiguration(true, "http://bot:8000", API_KEY, null, 45))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("BOT_MEMBER_ID");
        assertThatThrownBy(() -> new BotConfiguration(true, "http://bot:8000", "short", 7L, 45))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("BOT_API_KEY");
        assertThatCode(() -> new BotConfiguration(false, "", "", null, 45)).doesNotThrowAnyException();
    }

    private static BotDtos.ReplyRequest request() {
        return new BotDtos.ReplyRequest(
            "c-1",
            1L,
            "m-1",
            "Olá",
            List.of(new BotDtos.HistoryMessage("INBOUND", "Oi", Instant.parse("2026-11-21T10:00:00Z"))),
            List.of(new BotDtos.Department(3L, "Financeiro", "Boletos"))
        );
    }
}
