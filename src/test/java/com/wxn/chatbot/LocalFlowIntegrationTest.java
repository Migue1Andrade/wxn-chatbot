package com.wxn.chatbot;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Explicit opt-in: uses real local databases and leaves uniquely named reviewable records. */
@EnabledIfEnvironmentVariable(named = "WXN_INTEGRATION_TEST", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "wxn.dev.api-key=integration-test-key-minimum-32-characters"
)
class LocalFlowIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MongoTemplate mongo;

    @Autowired
    com.wxn.chatbot.whatsapp.config.TwilioProperties twilio;

    private static final String KEY = "integration-test-key-minimum-32-characters";

    private String cookie;
    private String csrf;

    @Autowired
    com.wxn.chatbot.user.service.UserService accountUsers;

    static com.wxn.chatbot.user.dto.UserDtos.Create account(
        String name,
        com.wxn.chatbot.user.entity.UserRole role,
        List<Long> departments
    ) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String digits = String.format(
            "%09d",
            java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999)
        );
        for (int size = 9; size <= 10; size++) {
            int sum = 0;
            for (int index = 0; index < size; index++) {
                sum += (digits.charAt(index) - '0') * (size + 1 - index);
            }
            int remainder = sum % 11;
            digits += remainder < 2 ? "0" : Integer.toString(11 - remainder);
        }
        return new com.wxn.chatbot.user.dto.UserDtos.Create(
            name,
            "fixture" + suffix,
            suffix + "@example.invalid",
            digits,
            null,
            "fixture-password-local",
            "fixture-password-local",
            role,
            com.wxn.chatbot.common.ActiveStatus.ACTIVE,
            departments
        );
    }

    @org.junit.jupiter.api.BeforeEach
    void authenticateAdministrator() {
        var request = account(
            "Administrador de verificação",
            com.wxn.chatbot.user.entity.UserRole.ADMIN,
            List.of()
        );
        accountUsers.create(request);
        var headers = new HttpHeaders();
        headers.set("X-Dev-Api-Key", KEY);
        var initial = http.exchange(
            "/api/auth/session",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class
        );
        cookie = initial.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
        headers.set(HttpHeaders.COOKIE, cookie);
        headers.set("X-CSRF-Token", initial.getBody().path("csrfToken").asText());
        var login = http.exchange(
            "/api/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("username", request.username(), "password", request.password()), headers),
            JsonNode.class
        );
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        cookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
        csrf = login.getBody().path("csrfToken").asText();
    }

    @Test
    void realPostgresMongoHttpFlowAndChannelIsolation() {
        assertThat(
            jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success = true", String.class)
        ).contains("1", "2", "3", "4");
        String base = "/api";
        long department = post(base + "/departments", Map.of("name", "Suporte", "color", "#2563EB"), 201)
            .path("id")
            .asLong();
        long human = post(
            base + "/users",
            account("Humano", com.wxn.chatbot.user.entity.UserRole.ATTENDANT, List.of(department)),
            201
        )
            .path("memberId")
            .asLong();
        long bot = post(base + "/members", Map.of("name", "Bot", "type", "BOT"), 201)
            .path("id")
            .asLong();
        long channel = post(
            base + "/whatsapp/channels",
            Map.of("name", "Mock", "provider", "MOCK", "phoneNumber", "+14155238886"),
            201
        )
            .path("id")
            .asLong();
        long otherChannel = post(
            base + "/whatsapp/channels",
            Map.of("name", "Twilio", "provider", "TWILIO", "phoneNumber", "+14155238886"),
            201
        )
            .path("id")
            .asLong();
        var inbound = Map.of(
            "channelId",
            channel,
            "externalMessageId",
            "integration-" + UUID.randomUUID(),
            "senderPhone",
            "+5581888888888",
            "content",
            "Olá"
        );
        var first = post("/api/dev/whatsapp/mock/inbound", inbound, 200);
        assertThat(post("/api/dev/whatsapp/mock/inbound", inbound, 200).path("id")).isEqualTo(
            first.path("id")
        );
        String id = first.path("conversationId").asText();
        String messages = base + "/conversations/" + id + "/messages";
        assertThat(get(messages, 200)).hasSize(1);
        post(messages, Map.of("content", "operationId ausente"), 400);
        for (long member : List.of(human, bot)) {
            var sent = post(
                messages,
                Map.of(
                    "channelId",
                    channel,
                    "memberId",
                    member,
                    "content",
                    "Resposta",
                    "operationId",
                    UUID.randomUUID().toString()
                ),
                201
            );
            assertThat(sent.path("status").asText()).isEqualTo("SIMULATED");
            assertThat(sent.path("provider").asText()).isEqualTo("MOCK");
            assertThat(sent.path("memberId").asLong()).isNotEqualTo(member);
        }
        assertThat(get(messages, 200)).hasSize(3);
        assertThat(
            http
                .getForEntity(base + "/members", String.class)
                .getStatusCode()
                .value()
        ).isEqualTo(401);
        // Local signed Twilio fixtures exercise the real HTTP ingress, NOT a real Twilio account.
        twilio.setChannelId(otherChannel);
        twilio.setChannelKey("integration-sandbox");
        twilio.setAccountSid("ACintegration");
        twilio.setAuthToken("local-test-signing-token");
        twilio.setPhoneNumber("+14155238886");
        twilio.setPublicBaseUrl("https://test.example");
        String external = "SM" + UUID.randomUUID().toString().replace("-", "");
        var twilioInbound = Map.of(
            "AccountSid",
            "ACintegration",
            "MessageSid",
            external,
            "From",
            "whatsapp:+5581888888888",
            "To",
            "whatsapp:+14155238886",
            "Body",
            "Twilio fixture"
        );
        signedTwilio(twilioInbound, false, 200);
        signedTwilio(twilioInbound, false, 200);
        var twilioConversations = get(base + "/conversations", 200);
        String twilioConversation = null;
        for (var c : twilioConversations) {
            if (c.path("channelId").asLong() == otherChannel) {
                twilioConversation = c.get("id").asText();
            }
        }
        assertThat(twilioConversation).isNotNull();
        String twilioHistory = base + "/conversations/" + twilioConversation + "/messages";
        var twilioReceived = get(twilioHistory, 200);
        assertThat(twilioReceived).hasSize(1);
        assertThat(twilioReceived.get(0).get("provider").asText()).isEqualTo("TWILIO");
        assertThat(twilioReceived.get(0).get("simulated").asBoolean()).isFalse();
        String outboundSid = "SM" + UUID.randomUUID().toString().replace("-", "");
        mongo
            .getCollection("messages")
            .insertOne(
                new org.bson.Document("channelId", otherChannel)
                    .append("conversationId", new org.bson.types.ObjectId(twilioConversation))
                    .append("externalMessageId", outboundSid)
                    .append("provider", "TWILIO")
                    .append("direction", "OUTBOUND")
                    .append("type", "TEXT")
                    .append("content", new org.bson.Document("text", "Status fixture"))
                    .append("status", "ACCEPTED")
                    .append("createdAt", new Date())
            );
        for (String status : List.of("read", "delivered", "queued", "read")) {
            signedTwilio(
                Map.of("AccountSid", "ACintegration", "MessageSid", outboundSid, "MessageStatus", status),
                true,
                204
            );
        }
        var statusHistory = get(twilioHistory, 200);
        assertThat(statusHistory).hasSize(2);
        for (var m : statusHistory) {
            if (outboundSid.equals(m.path("externalMessageId").asText())) {
                assertThat(m.get("status").asText()).isEqualTo("READ");
            }
        }
    }

    @Test
    void mockReleaseActionsAndUnknownReconciliationUseCanonicalPersistence() {
        long channel = post(
            "/api/whatsapp/channels",
            Map.of("name", "RC Mock " + UUID.randomUUID(), "provider", "MOCK", "phoneNumber", "+12025550190"),
            201
        )
            .path("id")
            .asLong();
        var incoming = Map.of(
            "channelId",
            channel,
            "senderPhone",
            "+12025550100",
            "content",
            "Dúvida de demonstração",
            "externalMessageId",
            "rc-in-" + UUID.randomUUID()
        );
        var received = post("/api/dev/whatsapp/mock/inbound", incoming, 200);
        String conversation = received.path("conversationId").asText();
        String history = "/api/conversations/" + conversation + "/messages";
        assertThat(post("/api/dev/whatsapp/mock/inbound", incoming, 200).path("id")).isEqualTo(
            received.path("id")
        );
        assertThat(
            get("/api/conversations/" + conversation, 200)
                .path("contactId")
                .asText()
        ).isNotBlank();
        assertThat(
            get("/api/admin/channels/" + channel + "/diagnostic", 200)
                .path("connection")
                .path("state")
                .asText()
        ).isEqualTo("CONNECTED");
        var command = Map.of("content", "Resposta local", "operationId", UUID.randomUUID().toString());
        var sent = post(history, command, 201);
        assertThat(post(history, command, 201).path("id")).isEqualTo(sent.path("id"));
        String id = sent.path("id").asText();
        assertThat(
            mutation(id, "EDIT_TEXT", "Texto corrigido", 200).path("content").path("text").asText()
        ).isEqualTo("Texto corrigido");
        assertThat(mutation(id, "SEND_REACTION", "👍", 200).path("reaction").asText()).isEqualTo("👍");
        assertThat(mutation(id, "REMOVE_REACTION", null, 200).path("reaction").asText("")).isEmpty();
        mutation(received.path("id").asText(), "MARK_READ", null, 409);
        post("/api/admin/channels/" + channel + "/remote-read-policy", Map.of("enabled", true), 200);
        assertThat(
            mutation(received.path("id").asText(), "MARK_READ", null, 200)
                .path("pendingMutation")
                .path("state")
                .asText()
        ).isEqualTo("CONFIRMED");
        var reply = post(
            history + "/" + received.path("id").asText() + "/reply",
            Map.of("content", "Resposta citada", "operationId", UUID.randomUUID().toString()),
            200
        );
        assertThat(reply.path("replyToMessageId")).isEqualTo(received.path("id"));
        String external = sent.path("externalMessageId").asText();
        for (String status : List.of("READ", "DELIVERED", "READ")) {
            post(
                "/api/dev/whatsapp/mock/status",
                Map.of("channelId", channel, "externalMessageId", external, "status", status),
                200
            );
        }
        assertThat(
            mongo
                .getCollection("messages")
                .find(new org.bson.Document("_id", new org.bson.types.ObjectId(id)))
                .first()
                .getString("status")
        ).isEqualTo("READ");
        var destination = post(
            "/api/dev/whatsapp/mock/inbound",
            Map.of(
                "channelId",
                channel,
                "senderPhone",
                "+12025550101",
                "content",
                "Destino",
                "externalMessageId",
                "rc-dest-" + UUID.randomUUID()
            ),
            200
        )
            .path("conversationId")
            .asText();
        var forwarded = post(
            "/api/messages/" + id + "/forward",
            Map.of("destinationConversationId", destination, "operationId", UUID.randomUUID().toString()),
            200
        );
        assertThat(forwarded.path("forwardedFromMessageId").asText()).isEqualTo(id);
        var deleted = exchange(
            "/api/messages/" + id + "?intent=DELETE_FOR_EVERYONE&operationId=" + UUID.randomUUID(),
            HttpMethod.DELETE,
            null,
            200
        );
        assertThat(deleted.path("deletedAt").isNull()).isFalse();
        assertThat(deleted.path("content").isNull()).isTrue();
        post("/api/dev/whatsapp/mock/scenario", Map.of("channelId", channel, "scenario", "TIMEOUT"), 200);
        var uncertain = post(
            history,
            Map.of("content", "Resposta incerta", "operationId", UUID.randomUUID().toString()),
            201
        );
        assertThat(uncertain.path("status").asText()).isEqualTo("UNKNOWN");
        int before = get(history, 200).size();
        String lateId = "mock-late-" + UUID.randomUUID();
        post(
            "/api/dev/whatsapp/mock/api-echo",
            Map.of(
                "channelId",
                channel,
                "senderPhone",
                "+12025550100",
                "content",
                "Resposta incerta",
                "externalMessageId",
                lateId
            ),
            200
        );
        var reconciled = post(
            "/api/admin/channels/" + channel + "/reconcile",
            Map.of("messageId", uncertain.path("id").asText(), "externalMessageId", lateId),
            200
        );
        assertThat(reconciled.path("status").asText()).isEqualTo("SENT");
        assertThat(get(history, 200)).hasSize(before);
        for (String operation : List.of("disconnect", "restart")) {
            post(
                "/api/admin/channels/" + channel + "/operation",
                Map.of(
                    "operation",
                    operation,
                    "operationId",
                    UUID.randomUUID().toString(),
                    "confirmation",
                    "CONFIRM"
                ),
                200
            );
            assertThat(
                get("/api/admin/channels/" + channel + "/diagnostic", 200)
                    .path("connection")
                    .path("state")
                    .asText()
            ).isEqualTo(operation.equals("disconnect") ? "DISCONNECTED" : "CONNECTED");
        }
    }

    private JsonNode mutation(String id, String operation, String value, int status) {
        Map<String, Object> body = new HashMap<>();
        body.put("operation", operation);
        body.put("operationId", UUID.randomUUID().toString());
        body.put("value", value);
        return post("/api/messages/" + id + "/operations", body, status);
    }

    private void signedTwilio(Map<String, String> form, boolean status, int expected) {
        var fields = new org.springframework.util.LinkedMultiValueMap<String, String>();
        form.forEach(fields::add);
        String url = status ? twilio.statusUrl() : twilio.inboundUrl();
        StringBuilder input = new StringBuilder(url);
        new TreeMap<>(form).forEach((key, value) -> input.append(key).append(value));
        String signature;
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(
                new javax.crypto.spec.SecretKeySpec(
                    twilio.getAuthToken().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "HmacSHA1"
                )
            );
            signature = Base64.getEncoder().encodeToString(
                mac.doFinal(input.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        } catch (java.security.GeneralSecurityException error) {
            throw new AssertionError(error);
        }
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("X-Twilio-Signature", signature);
        String path = "/api/webhooks/whatsapp/twilio/" + twilio.getChannelKey() + (status ? "/status" : "");
        assertThat(
            http
                .exchange(path, HttpMethod.POST, new HttpEntity<>(fields, headers), String.class)
                .getStatusCode()
                .value()
        ).isEqualTo(expected);
    }

    private JsonNode post(String url, Object body, int status) {
        return exchange(url, HttpMethod.POST, body, status);
    }

    private JsonNode get(String url, int status) {
        return exchange(url, HttpMethod.GET, null, status);
    }

    private JsonNode exchange(String url, HttpMethod method, Object body, int status) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Dev-Api-Key", KEY);
        headers.set(HttpHeaders.COOKIE, cookie);
        headers.set("X-CSRF-Token", csrf);
        var response = http.exchange(url, method, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(response.getStatusCode().value())
            .as(method + " " + url)
            .isEqualTo(status);
        return response.getBody();
    }
}
