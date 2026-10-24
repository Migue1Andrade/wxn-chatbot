package com.wxn.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in: uses real local databases and leaves uniquely named reviewable records. */
@EnabledIfEnvironmentVariable(named="WXN_INTEGRATION_TEST", matches="true")
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties="wxn.dev.api-key=integration-test-key-minimum-32-characters")
class LocalFlowIntegrationTest {
// Test-only wider dev context exercises existing business tenant boundaries.
@org.springframework.test.context.bean.override.mockito.MockitoSpyBean
com.wxn.chatbot.config.DevelopmentAccess developmentAccess;
@org.junit.jupiter.api.BeforeEach void allowBusinessIsolationFixtures() {
    org.mockito.Mockito.doReturn(true).when(developmentAccess).allows(org.mockito.ArgumentMatchers.anyLong());
}
@Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired MongoTemplate mongo;
    @Autowired com.wxn.chatbot.whatsapp.config.TwilioProperties twilio;
    private static final String KEY = "integration-test-key-minimum-32-characters";
    @Test void realPostgresMongoHttpFlowAndTenantConstraints() {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success = true", String.class))
                .contains("1", "2", "3", "4", "5");
        long company = post("/api/companies", Map.of("name", "Integration " + UUID.randomUUID()), 201).get("id").asLong();
        long other = post("/api/companies", Map.of("name", "Other " + UUID.randomUUID()), 201).get("id").asLong();
        String base = "/api/companies/" + company;
        long user = post(base + "/users", Map.of("name", "Humano", "phone", "+55 (81) 99999-9999"), 201).get("id").asLong();
        post(base + "/users", Map.of("name", "Duplicado", "phone", "+5581999999999"), 409);
        post(base + "/users", Map.of("name", "Inválido", "phone", "81999999999"), 400);
        long human = post(base + "/members", Map.of("name", "Humano", "type", "HUMAN", "userId", user), 201).get("id").asLong();
        post(base + "/members", Map.of("name", "Duplicado", "type", "HUMAN", "userId", user), 409);
        post("/api/companies/" + other + "/members", Map.of("name", "Outra empresa", "type", "HUMAN", "userId", user), 404);
        long bot = post(base + "/members", Map.of("name", "Bot", "type", "BOT"), 201).get("id").asLong();
        long channel = post(base + "/whatsapp/channels", Map.of("name", "Mock", "provider", "MOCK", "phoneNumber", "+14155238886"), 201).get("id").asLong();
        long otherChannel = post(base + "/whatsapp/channels", Map.of("name", "Twilio", "provider", "TWILIO", "phoneNumber", "+14155238886"), 201).get("id").asLong();
        var inbound = Map.of("companyId", company, "channelId", channel, "externalMessageId", "integration-" + UUID.randomUUID(), "senderPhone", "+5581888888888", "content", "Olá");
        var first = post("/api/dev/whatsapp/mock/inbound", inbound, 200);
        var duplicate = post("/api/dev/whatsapp/mock/inbound", inbound, 200);
        assertThat(duplicate.get("id")).isEqualTo(first.get("id"));
        String id = first.get("conversationId").asText();
        String messages = base + "/conversations/" + id + "/messages";
        assertThat(get(messages, 200)).hasSize(1);
        assertThat(get(base + "/conversations", 200)).hasSize(1);
        get("/api/companies/" + other + "/conversations/" + id, 404);
        get("/api/companies/" + other + "/conversations/" + id + "/messages", 404);
        post(messages, Map.of("channelId", otherChannel, "memberId", human, "content", "Canal errado"), 400);
        long foreignBot = post("/api/companies/" + other + "/members", Map.of("name", "Outro bot", "type", "BOT"), 201).get("id").asLong();
        post(messages, Map.of("channelId", channel, "memberId", foreignBot, "content", "Membro errado"), 404);
        for (long member : List.of(human, bot)) {
            var sent = post(messages, Map.of("channelId", channel, "memberId", member, "content", "Resposta"), 201);
            assertThat(sent.get("status").asText()).isEqualTo("SIMULATED");
            assertThat(sent.get("provider").asText()).isEqualTo("MOCK");
            assertThat(sent.get("simulated").asBoolean()).isTrue();
        }
        assertThat(get(messages, 200)).hasSize(3);
        assertThat(mongo.getCollection("message_logs").countDocuments(new org.bson.Document("companyId", company))).isGreaterThanOrEqualTo(5);
        assertThat(http.getForEntity(base, String.class).getStatusCode().value()).isEqualTo(401);
// NULL phones remain allowed; the same populated phone can exist in another tenant.
post(base + "/users", Map.of("name", "Sem telefone"), 201);
post(base + "/users", Map.of("name", "Também sem telefone"), 201);
post("/api/companies/" + other + "/users", Map.of("name", "Outro", "phone", "+5581999999999"), 201);
long foreignChannel = post("/api/companies/" + other + "/whatsapp/channels",
        Map.of("name", "Outro Mock", "provider", "MOCK", "phoneNumber", "+14155238886"), 201).get("id").asLong();
post("/api/dev/whatsapp/mock/inbound", Map.of("companyId", company, "channelId", foreignChannel,
        "externalMessageId", "wrong-tenant", "senderPhone", "+5581888888888", "content", "Inválido"), 404);
var parallelPayload = Map.of("companyId", company, "channelId", channel, "externalMessageId", "concurrent-" + UUID.randomUUID(),
        "senderPhone", "+5581888888888", "content", "Concorrente");
var a = java.util.concurrent.CompletableFuture.supplyAsync(() -> post("/api/dev/whatsapp/mock/inbound", parallelPayload, 200));
var b = java.util.concurrent.CompletableFuture.supplyAsync(() -> post("/api/dev/whatsapp/mock/inbound", parallelPayload, 200));
assertThat(a.join().get("id")).isEqualTo(b.join().get("id"));
assertThat(get(messages, 200)).hasSize(4);
// Legacy documents lack channel/key; partial unique indexes and canonical reads still work.
var legacyId = new org.bson.types.ObjectId();
mongo.getCollection("conversations").insertOne(new org.bson.Document("_id", legacyId).append("companyId", company)
        .append("status", "OPEN").append("createdAt", new Date()).append("updatedAt", new Date()));
mongo.getCollection("conversations").insertOne(new org.bson.Document("companyId", company).append("status", "OPEN"));
mongo.getCollection("messages").insertOne(new org.bson.Document("companyId", company).append("conversationId", legacyId)
        .append("direction", "inbound").append("type", "text").append("content", new org.bson.Document("text", "Legado"))
        .append("createdAt", new Date()));
var legacy = get(base + "/conversations/" + legacyId.toHexString() + "/messages", 200);
assertThat(legacy.get(0).get("messageType").asText()).isEqualTo("TEXT");
assertThat(legacy.get(0).get("createdAt").isNull()).isFalse();
// Local signed Twilio fixtures exercise the real HTTP ingress, NOT a real Twilio account.
twilio.setCompanyId(company); twilio.setChannelId(otherChannel); twilio.setChannelKey("integration-sandbox");
twilio.setAccountSid("ACintegration"); twilio.setAuthToken("local-test-signing-token");
twilio.setPhoneNumber("+14155238886"); twilio.setPublicBaseUrl("https://test.example");
String external = "SM" + UUID.randomUUID().toString().replace("-", "");
var twilioInbound = Map.of("AccountSid", "ACintegration", "MessageSid", external,
        "From", "whatsapp:+5581888888888", "To", "whatsapp:+14155238886", "Body", "Twilio fixture");
signedTwilio(twilioInbound, false, 200); signedTwilio(twilioInbound, false, 200);
var twilioConversations = get(base + "/conversations", 200);
String twilioConversation = null;
for (var c : twilioConversations) if (c.path("channelId").asLong() == otherChannel) twilioConversation = c.get("id").asText();
assertThat(twilioConversation).isNotNull();
String twilioHistory = base + "/conversations/" + twilioConversation + "/messages";
var twilioReceived = get(twilioHistory, 200);
assertThat(twilioReceived).hasSize(1);
assertThat(twilioReceived.get(0).get("provider").asText()).isEqualTo("TWILIO");
assertThat(twilioReceived.get(0).get("simulated").asBoolean()).isFalse();
String outboundSid = "SM" + UUID.randomUUID().toString().replace("-", "");
mongo.getCollection("messages").insertOne(new org.bson.Document("companyId", company).append("channelId", otherChannel)
        .append("conversationId", new org.bson.types.ObjectId(twilioConversation)).append("externalMessageId", outboundSid)
        .append("provider", "TWILIO").append("direction", "OUTBOUND").append("type", "TEXT")
        .append("content", new org.bson.Document("text", "Status fixture")).append("status", "ACCEPTED").append("createdAt", new Date()));
for (String status : List.of("read", "delivered", "queued", "read")) {
    signedTwilio(Map.of("AccountSid", "ACintegration", "MessageSid", outboundSid, "MessageStatus", status), true, 204);
}
var statusHistory = get(twilioHistory, 200);
assertThat(statusHistory).hasSize(2);
for (var m : statusHistory) if (outboundSid.equals(m.path("externalMessageId").asText())) assertThat(m.get("status").asText()).isEqualTo("READ");
// DB-level tenant FK also protects callers bypassing the service.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO members(company_id,user_id,name,type,status,created_at,updated_at) VALUES (?,?,'Invalid','HUMAN','ACTIVE',now(),now())", other, user))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
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
        mac.init(new javax.crypto.spec.SecretKeySpec(twilio.getAuthToken().getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA1"));
        signature = Base64.getEncoder().encodeToString(mac.doFinal(input.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException error) { throw new AssertionError(error); }
    var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.set("X-Twilio-Signature", signature);
    String path = "/api/webhooks/whatsapp/twilio/" + twilio.getChannelKey() + (status ? "/status" : "");
    assertThat(http.exchange(path, HttpMethod.POST, new HttpEntity<>(fields, headers), String.class).getStatusCode().value()).isEqualTo(expected);
}
private JsonNode post(String url, Object body, int status) { return exchange(url, HttpMethod.POST, body, status); }
    private JsonNode get(String url, int status) { return exchange(url, HttpMethod.GET, null, status); }
    private JsonNode exchange(String url, HttpMethod method, Object body, int status) {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON); headers.set("X-Dev-Api-Key", KEY);
        var response = http.exchange(url, method, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(response.getStatusCode().value()).as(method + " " + url).isEqualTo(status);
        return response.getBody();
    }
}
