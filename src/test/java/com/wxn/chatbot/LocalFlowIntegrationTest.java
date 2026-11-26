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
@ActiveProfiles({ "dev", "legacy" })
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "wxn.dev.api-key=integration-test-key-minimum-32-characters",
        "openbot.support.enabled=true",
    }
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

    @Test
    void analyticsOverviewFiltersHistoricalWindowByChannelAndKeepsEmptyDaysAndTodaySeparate() {
        var start = java.time.Instant.parse("2025-02-01T00:00:00Z");
        var end = java.time.Instant.parse("2025-02-03T23:59:59Z");
        long resolvedTodayBefore = get("/api/admin/kanban/metrics", 200).path("resolvedToday").asLong();
        long firstChannel = java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000);
        long secondChannel = firstChannel + 1;
        long currentChannel = secondChannel + 1;
        var now = java.time.Instant.now();
        mongo.getCollection("conversations").insertMany(
            List.of(
                new org.bson.Document("status", "RESOLVED")
                    .append("channelId", firstChannel)
                    .append("conversationKey", "audit-first-" + UUID.randomUUID())
                    .append("createdAt", java.util.Date.from(start))
                    .append(
                        "resolutionCycles",
                        List.of(
                            new org.bson.Document("startedAt", java.util.Date.from(start)).append(
                                "resolvedAt",
                                java.util.Date.from(start.plusSeconds(600))
                            )
                        )
                    ),
                new org.bson.Document("status", "RESOLVED")
                    .append("channelId", secondChannel)
                    .append("conversationKey", "audit-second-" + UUID.randomUUID())
                    .append("createdAt", java.util.Date.from(start.plusSeconds(86400)))
                    .append(
                        "resolutionCycles",
                        List.of(
                            new org.bson.Document("startedAt", java.util.Date.from(start)).append(
                                "resolvedAt",
                                java.util.Date.from(start.plusSeconds(1200))
                            )
                        )
                    )
            )
        );
        mongo.getCollection("conversations").insertOne(
            new org.bson.Document("status", "RESOLVED")
                .append("channelId", currentChannel)
                .append("conversationKey", "audit-current-" + UUID.randomUUID())
                .append("createdAt", java.util.Date.from(now))
                .append(
                    "resolutionCycles",
                    List.of(
                        new org.bson.Document("startedAt", java.util.Date.from(now.minusSeconds(900))).append(
                            "resolvedAt",
                            java.util.Date.from(now)
                        )
                    )
                )
        );
        var first = get(
            "/api/admin/analytics/overview?from=2025-02-01&to=2025-02-03&channelId=" + firstChannel,
            200
        );
        assertThat(first.path("dailyVolume")).hasSize(3);
        assertThat(first.path("dailyVolume").get(0).path("opened").asLong()).isEqualTo(1);
        assertThat(first.path("dailyVolume").get(1).path("opened").asLong()).isZero();
        assertThat(first.path("averageResolutionMinutes").asDouble()).isEqualTo(10.0);
        assertThat(first.path("resolvedToday").asLong()).isEqualTo(resolvedTodayBefore + 1);
        var second = get(
            "/api/admin/analytics/overview?from=2025-02-01&to=2025-02-03&channelId=" + secondChannel,
            200
        );
        assertThat(second.path("dailyVolume").get(1).path("opened").asLong()).isEqualTo(1);
        assertThat(second.path("averageResolutionMinutes").asDouble()).isEqualTo(20.0);
        assertThat(get("/api/admin/kanban/metrics", 200).path("resolvedToday").asLong()).isEqualTo(
            resolvedTodayBefore + 1
        );
        assertThat(get("/api/admin/analytics/overview?from=2025-02-03&to=2025-02-01", 400)).isNotNull();
    }

    private String cookie;
    private String csrf;
    private long administrator;

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
        administrator = accountUsers.create(request).memberId();
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
        post(
            messages,
            Map.of("content", "Antes de assumir", "operationId", UUID.randomUUID().toString()),
            403
        );
        assume(id);
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
            assertThat(sent.path("memberId").asLong()).isEqualTo(administrator);
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
        post(history, command, 403);
        assume(conversation);
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
        post(
            "/api/messages/" + id + "/forward",
            Map.of("destinationConversationId", destination, "operationId", UUID.randomUUID().toString()),
            403
        );
        assume(destination);
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

    @Autowired
    com.wxn.chatbot.conversation.service.ConversationManagementService management;

    @Autowired
    com.wxn.chatbot.task.service.TaskService tasks;

    @Test
    void followUpCanBeScheduledFilteredRemindedRescheduledAndCompleted() {
        long channel = post(
            "/api/whatsapp/channels",
            Map.of("name", "Follow-up Mock", "provider", "MOCK", "phoneNumber", "+12025550210"),
            201
        )
            .path("id")
            .asLong();
        String conversationId = post(
            "/api/dev/whatsapp/mock/inbound",
            Map.of(
                "channelId",
                channel,
                "senderPhone",
                "+12025550211",
                "content",
                "Fixture de follow-up",
                "externalMessageId",
                UUID.randomUUID().toString()
            ),
            200
        )
            .path("conversationId")
            .asText();
        var dueAt = java.time.Instant.now().minusSeconds(60);
        var input = new HashMap<String, Object>();
        input.put("type", "FOLLOW_UP");
        input.put("title", "Retornar sobre test-drive");
        input.put("description", "Confirmar horário escolhido");
        input.put("conversationId", conversationId);
        input.put("dueAt", dueAt.toString());
        input.put("status", "OPEN");
        input.put("priority", "NORMAL");
        var created = post("/api/tasks", input, 200);
        long taskId = created.path("id").asLong();
        assertThat(created.path("assignedUserId").asLong()).isEqualTo(
            created.path("createdByUserId").asLong()
        );
        assertThat(get("/api/tasks?type=FOLLOW_UP&period=OVERDUE", 200).findValuesAsText("id")).contains(
            Long.toString(taskId)
        );
        tasks.notifyOverdue();
        tasks.notifyOverdue();
        var notifications = get("/api/notifications", 200);
        var reminders = new java.util.ArrayList<JsonNode>();
        notifications.forEach(row -> {
            if (
                "TASK_OVERDUE".equals(row.path("type").asText()) &&
                Long.toString(taskId).equals(row.path("resourceId").asText())
            ) {
                reminders.add(row);
            }
        });
        assertThat(reminders).hasSize(1);
        assertThat(reminders.getFirst().path("title").asText()).contains("Retornar sobre test-drive");

        var attendant = account(
            "Atendente isolado follow-up",
            com.wxn.chatbot.user.entity.UserRole.ATTENDANT,
            List.of(
                post("/api/departments", Map.of("name", "Follow-up", "color", "#2563EB"), 201)
                    .path("id")
                    .asLong()
            )
        );
        accountUsers.create(attendant);
        var otherSession = attendantSession(attendant);
        var hidden = http.exchange("/api/tasks/" + taskId, HttpMethod.GET, otherSession, JsonNode.class);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var revised = new HashMap<>(input);
        revised.put("id", taskId);
        revised.put("assignedUserId", created.path("assignedUserId").asLong());
        revised.put(
            "expectedVersion",
            get("/api/tasks/" + taskId, 200)
                .path("version")
                .asLong()
        );
        revised.put("dueAt", java.time.Instant.now().plusSeconds(3600).toString());
        var rescheduled = exchange("/api/tasks/" + taskId, HttpMethod.PATCH, revised, 200);
        assertThat(get("/api/tasks?type=FOLLOW_UP&period=TODAY", 200).findValuesAsText("id")).contains(
            Long.toString(taskId)
        );
        assertThat(get("/api/notifications", 200).get(0).path("readAt").isNull()).isFalse();

        var upcoming = new HashMap<>(revised);
        upcoming.put("title", "Follow-up futuro de fixture");
        upcoming.put(
            "dueAt",
            java.time.Instant.now()
                .plusSeconds(48 * 3600L)
                .toString()
        );
        upcoming.remove("expectedVersion");
        upcoming.remove("id");
        var upcomingTask = post("/api/tasks", upcoming, 200);
        assertThat(get("/api/tasks?type=FOLLOW_UP&period=UPCOMING", 200).findValuesAsText("id")).contains(
            upcomingTask.path("id").asText()
        );

        revised.put("expectedVersion", rescheduled.path("version").asLong());
        revised.put("status", "COMPLETED");
        exchange("/api/tasks/" + taskId, HttpMethod.PATCH, revised, 200);
        assertThat(
            get("/api/tasks?type=FOLLOW_UP&period=OVERDUE", 200).findValuesAsText("id")
        ).doesNotContain(Long.toString(taskId));
        revised.put(
            "expectedVersion",
            get("/api/tasks/" + taskId, 200)
                .path("version")
                .asLong()
        );
        revised.put("status", "OPEN");
        revised.put("dueAt", java.time.Instant.now().minusSeconds(60).toString());
        exchange("/api/tasks/" + taskId, HttpMethod.PATCH, revised, 200);
        tasks.notifyOverdue();
        tasks.notifyOverdue();
        var afterReopen = get("/api/notifications", 200);
        var remindersAfterReopen = new java.util.ArrayList<JsonNode>();
        afterReopen.forEach(row -> {
            if (
                "TASK_OVERDUE".equals(row.path("type").asText()) &&
                Long.toString(taskId).equals(row.path("resourceId").asText())
            ) {
                remindersAfterReopen.add(row);
            }
        });
        assertThat(remindersAfterReopen).hasSize(2);
        assertThat(remindersAfterReopen.stream().filter(row -> row.path("readAt").isNull())).hasSize(1);
        assertThat(
            get("/api/conversations/" + conversationId, 200)
                .path("status")
                .asText()
        ).isEqualTo("PENDING");
    }

    @Test
    void twoAttendantsCanClaimOneBotHandoffOnlyOnce() {
        long department = post("/api/departments", Map.of("name", "Suporte", "color", "#2563EB"), 201)
            .path("id")
            .asLong();
        var first = account(
            "Atendente A",
            com.wxn.chatbot.user.entity.UserRole.ATTENDANT,
            List.of(department)
        );
        var second = account(
            "Atendente B",
            com.wxn.chatbot.user.entity.UserRole.ATTENDANT,
            List.of(department)
        );
        long firstMember = accountUsers.create(first).memberId();
        long secondMember = accountUsers.create(second).memberId();
        long bot = post("/api/members", Map.of("name", "AutoGuia", "type", "BOT"), 201)
            .path("id")
            .asLong();
        long channel = post(
            "/api/whatsapp/channels",
            Map.of("name", "Fila Mock", "provider", "MOCK", "phoneNumber", "+12025550110"),
            201
        )
            .path("id")
            .asLong();
        String id = post(
            "/api/dev/whatsapp/mock/inbound",
            Map.of(
                "channelId",
                channel,
                "senderPhone",
                "+12025550111",
                "content",
                "Quero falar com um atendente",
                "externalMessageId",
                UUID.randomUUID().toString()
            ),
            200
        )
            .path("conversationId")
            .asText();
        String path = "/api/conversations/" + id;
        long version = get(path, 200).path("version").asLong();
        var assigned = exchange(
            path + "/assign",
            HttpMethod.PATCH,
            Map.of("actorMemberId", administrator, "expectedVersion", version, "memberId", bot),
            200
        );
        management.handoffToQueue(
            id,
            new com.wxn.chatbot.conversation.dto.ConversationManagementDtos.Action(
                bot,
                assigned.path("version").asLong(),
                "Cliente solicitou atendimento humano"
            )
        );

        var firstSession = attendantSession(first);
        var secondSession = attendantSession(second);
        long firstOffer = offer(firstSession, id);
        long secondOffer = offer(secondSession, id);
        assertThat(firstOffer).isPositive();
        assertThat(secondOffer).isPositive();
        assertThat(
            http.exchange(path, HttpMethod.GET, firstSession, JsonNode.class).getStatusCode()
        ).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
            http
                .exchange("/api/notifications/read-all", HttpMethod.POST, firstSession, Void.class)
                .getStatusCode()
                .is2xxSuccessful()
        ).isTrue();
        assertThat(offer(firstSession, id)).isEqualTo(firstOffer);

        var ready = new java.util.concurrent.CountDownLatch(1);
        var firstClaim = concurrentClaim(firstSession, firstOffer, ready);
        var secondClaim = concurrentClaim(secondSession, secondOffer, ready);
        ready.countDown();
        var a = firstClaim.join();
        var b = secondClaim.join();
        assertThat(List.of(a.getStatusCode().value(), b.getStatusCode().value())).contains(200);
        assertThat(
            List.of(a, b)
                .stream()
                .filter(r -> r.getStatusCode().is2xxSuccessful())
                .count()
        ).isEqualTo(1);
        assertThat((a.getStatusCode().is2xxSuccessful() ? b : a).getStatusCode().value()).isIn(404, 409);
        long winner = a.getStatusCode().is2xxSuccessful() ? firstMember : secondMember;
        assertThat(get(path, 200).path("assignedMemberId").asLong()).isEqualTo(winner);
        assertThat(offer(firstSession, id)).isZero();
        assertThat(offer(secondSession, id)).isZero();
        var winnerSession = a.getStatusCode().is2xxSuccessful() ? firstSession : secondSession;
        var loserSession = a.getStatusCode().is2xxSuccessful() ? secondSession : firstSession;
        var message = Map.of("content", "Posso ajudar", "operationId", UUID.randomUUID().toString());
        var denied = http.exchange(
            path + "/messages",
            HttpMethod.POST,
            new HttpEntity<>(message, loserSession.getHeaders()),
            JsonNode.class
        );
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var sent = http.exchange(
            path + "/messages",
            HttpMethod.POST,
            new HttpEntity<>(message, winnerSession.getHeaders()),
            JsonNode.class
        );
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(sent.getBody().path("memberId").asLong()).isEqualTo(winner);
    }

    private HttpEntity<Void> attendantSession(com.wxn.chatbot.user.dto.UserDtos.Create account) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Dev-Api-Key", KEY);
        var initial = http.exchange(
            "/api/auth/session",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class
        );
        headers.set(
            HttpHeaders.COOKIE,
            initial.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0]
        );
        headers.set("X-CSRF-Token", initial.getBody().path("csrfToken").asText());
        var login = http.exchange(
            "/api/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("username", account.username(), "password", account.password()), headers),
            JsonNode.class
        );
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        headers.set(HttpHeaders.COOKIE, login.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0]);
        headers.set("X-CSRF-Token", login.getBody().path("csrfToken").asText());
        return new HttpEntity<>(headers);
    }

    private long offer(HttpEntity<Void> session, String conversationId) {
        var response = http.exchange("/api/notifications", HttpMethod.GET, session, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (var row : response.getBody()) {
            if (
                "CONVERSATION_AVAILABLE".equals(row.path("type").asText()) &&
                conversationId.equals(row.path("resourceId").asText())
            ) {
                return row.path("id").asLong();
            }
        }
        return 0;
    }

    private java.util.concurrent.CompletableFuture<ResponseEntity<JsonNode>> concurrentClaim(
        HttpEntity<Void> session,
        long offer,
        java.util.concurrent.CountDownLatch ready
    ) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                ready.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            return http.exchange(
                "/api/notifications/" + offer + "/claim",
                HttpMethod.POST,
                session,
                JsonNode.class
            );
        });
    }

    @Test
    void sameNumberAfterBotSessionEndStartsNewChatAndReplayStaysInOldChat() {
        long channel = post(
            "/api/whatsapp/channels",
            Map.of("name", "Sessões Mock", "provider", "MOCK", "phoneNumber", "+12025550130"),
            201
        )
            .path("id")
            .asLong();
        var initial = Map.of(
            "channelId",
            channel,
            "senderPhone",
            "+12025550131",
            "content",
            "Pergunta da primeira sessão",
            "externalMessageId",
            UUID.randomUUID().toString()
        );
        var oldMessage = post("/api/dev/whatsapp/mock/inbound", initial, 200);
        String oldId = oldMessage.path("conversationId").asText();
        String path = "/api/conversations/" + oldId;
        long bot = post("/api/members", Map.of("name", "Bot de sessões", "type", "BOT"), 201)
            .path("id")
            .asLong();
        var assigned = exchange(
            path + "/assign",
            HttpMethod.PATCH,
            Map.of(
                "actorMemberId",
                administrator,
                "expectedVersion",
                get(path, 200).path("version").asLong(),
                "memberId",
                bot
            ),
            200
        );
        management.cancelBotForInactivity(
            oldId,
            new com.wxn.chatbot.conversation.dto.ConversationManagementDtos.Action(
                bot,
                assigned.path("version").asLong(),
                "Inatividade por 30 minutos"
            )
        );
        assertThat(get(path, 200).path("botSessionEndedAt").asText()).isNotBlank();

        assertThat(post("/api/dev/whatsapp/mock/inbound", initial, 200).path("id")).isEqualTo(
            oldMessage.path("id")
        );
        var next = post(
            "/api/dev/whatsapp/mock/inbound",
            Map.of(
                "channelId",
                channel,
                "senderPhone",
                "+12025550131",
                "content",
                "Pergunta da segunda sessão",
                "externalMessageId",
                UUID.randomUUID().toString()
            ),
            200
        );
        String nextId = next.path("conversationId").asText();
        assertThat(nextId).isNotEqualTo(oldId);
        assertThat(
            get("/api/conversations/" + oldId, 200)
                .path("status")
                .asText()
        ).isEqualTo("CANCELLED");
        assertThat(get("/api/conversations/" + oldId + "/messages", 200)).hasSize(1);
        var nextHistory = get("/api/conversations/" + nextId + "/messages", 200);
        assertThat(nextHistory).hasSize(1);
        assertThat(nextHistory.get(0).path("content").path("text").asText()).isEqualTo(
            "Pergunta da segunda sessão"
        );
    }

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.wxn.chatbot.openbot.SupportProperties botSettings;

    @Autowired
    com.wxn.chatbot.openbot.OpenBotSupportService botSupport;

    @Test
    void mockBotAsksForConfirmationAndOpensFreshSessionAfterYes() throws InterruptedException {
        long channel = post(
            "/api/whatsapp/channels",
            Map.of("name", "Confirmação Mock", "provider", "MOCK", "phoneNumber", "+12025550140"),
            201
        )
            .path("id")
            .asLong();
        long bot = post("/api/members", Map.of("name", "Bot de confirmação", "type", "BOT"), 201)
            .path("id")
            .asLong();
        org.mockito.Mockito.when(botSettings.enabled()).thenReturn(true);
        org.mockito.Mockito.when(botSettings.autoAssignBotId()).thenReturn(bot);
        org.mockito.Mockito.when(botSettings.autoAssignChannelId()).thenReturn(channel);
        String id = mockSessionMessage(channel, "Quais documentos preciso levar?");
        String path = "/api/conversations/" + id;
        waitForStatus(path, "WAITING_CUSTOMER");
        assertThat(get(path + "/messages", 200).toString()).contains(
            "Todas as suas dúvidas foram solucionadas?"
        );
        assertThat(mockSessionMessage(channel, "Sim, obrigado!")).isEqualTo(id);
        waitForStatus(path, "RESOLVED");
        assertThat(get(path + "/messages", 200).toString()).contains("Obrigado pelo contato!");
        String nextId = mockSessionMessage(channel, "Olá");
        assertThat(nextId).isNotEqualTo(id);
        String nextPath = "/api/conversations/" + nextId;
        waitForStatus(nextPath, "WAITING_CUSTOMER");
        ageLastInbound(nextId, 20);
        botSupport.checkBotInactivity();
        assertThat(get(nextPath + "/messages", 200).toString()).contains("em 10 minutos");
        ageLastInbound(nextId, 30);
        botSupport.checkBotInactivity();
        waitForStatus(nextPath, "CANCELLED");
        assertThat(get(nextPath + "/messages", 200).toString()).contains("encerrado por inatividade");
        assertThat(mockSessionMessage(channel, "Olá")).isNotIn(id, nextId);
    }

    private void ageLastInbound(String id, int minutes) {
        var timestamp = Date.from(java.time.Instant.now().minusSeconds(minutes * 60L));
        mongo
            .getCollection("conversations")
            .updateOne(
                new org.bson.Document("_id", new org.bson.types.ObjectId(id)),
                new org.bson.Document("$set", new org.bson.Document("lastInboundAt", timestamp))
            );
    }

    private String mockSessionMessage(long channel, String text) {
        return post(
            "/api/dev/whatsapp/mock/inbound",
            Map.of(
                "channelId",
                channel,
                "senderPhone",
                "+12025550141",
                "content",
                text,
                "externalMessageId",
                UUID.randomUUID().toString()
            ),
            200
        )
            .path("conversationId")
            .asText();
    }

    private void waitForStatus(String path, String status) throws InterruptedException {
        var deadline = java.time.Instant.now().plusSeconds(10);
        String actual = get(path, 200).path("status").asText();
        while (!status.equals(actual) && java.time.Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
            actual = get(path, 200).path("status").asText();
        }
        assertThat(actual).isEqualTo(status);
    }

    private void assume(String id) {
        String path = "/api/conversations/" + id;
        long version = get(path, 200).path("version").asLong();
        exchange(
            path + "/assume",
            HttpMethod.PATCH,
            Map.of("actorMemberId", administrator, "expectedVersion", version),
            200
        );
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
