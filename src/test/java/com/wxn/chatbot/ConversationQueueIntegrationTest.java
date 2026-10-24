package com.wxn.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.wxn.chatbot.config.DevelopmentAccess;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.StreamSupport;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;

/** Opt-in HTTP/service/repository tests against real PostgreSQL and MongoDB. */
@EnabledIfEnvironmentVariable(named = "WXN_INTEGRATION_TEST", matches = "true")
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "wxn.dev.api-key=integration-test-key-minimum-32-characters")
class ConversationQueueIntegrationTest {
    private static final String KEY = "integration-test-key-minimum-32-characters";
    private static final Instant TIME = Instant.parse("2026-01-01T12:00:00Z");
    @Autowired TestRestTemplate http;
    @Autowired MongoTemplate mongo;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean DevelopmentAccess access;
    private Tenant tenant;
    private Tenant other;

    record Tenant(long company, long human, long bot, long channel) {}

    @BeforeEach
    void fixtures() {
        // Wider test-only context exercises real business tenant boundaries.
        doReturn(true).when(access).allows(anyLong());
        tenant = tenant();
        other = tenant();
    }

    private Tenant tenant() {
        long company = call("/api/companies", HttpMethod.POST,
            Map.of("name", "Queue test " + UUID.randomUUID()), 201).path("id").asLong();
        String base = "/api/companies/" + company;
        long user = call(base + "/users", HttpMethod.POST, Map.of("name", "Ana"), 201).path("id").asLong();
        long human = call(base + "/members", HttpMethod.POST,
            Map.of("name", "Ana", "type", "HUMAN", "userId", user), 201).path("id").asLong();
        long bot = call(base + "/members", HttpMethod.POST,
            Map.of("name", "Bot", "type", "BOT"), 201).path("id").asLong();
        long channel = call(base + "/whatsapp/channels", HttpMethod.POST,
            Map.of("name", "Mock", "provider", "MOCK", "phoneNumber", "+14155238886"), 201)
            .path("id").asLong();
        return new Tenant(company, human, bot, channel);
    }

    private String base(Tenant t) { return "/api/companies/" + t.company() + "/conversations"; }
    private String base() { return base(tenant); }
    private JsonNode get(String path) { return call(path, HttpMethod.GET, null, 200); }
    private JsonNode queue(String query) { return get(base() + "?" + query); }
    private static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    private static List<String> ids(JsonNode rows) {
        return StreamSupport.stream(rows.spliterator(), false).map(row -> row.path("id").asText()).toList();
    }

    private String conversation(Tenant t, String name, String status, Long member, Instant time) {
        ObjectId id = new ObjectId();
        mongo.getCollection("conversations").insertOne(new Document("_id", id)
            .append("companyId", t.company()).append("channelId", t.channel())
            .append("provider", "MOCK").append("conversationType", "INDIVIDUAL")
            .append("contactPhone", "+5581999999999").append("displayName", name)
            .append("externalChatId", "external-" + name).append("status", status)
            .append("assignedMemberId", member).append("version", 0L)
            .append("createdAt", Date.from(time)).append("updatedAt", Date.from(time)));
        return id.toHexString();
    }

    private String conversation(String name, String status, Long member, Instant time) {
        return conversation(tenant, name, status, member, time);
    }

    private String message(Tenant t, String conversation, String direction, Instant time) {
        ObjectId id = new ObjectId();
        mongo.getCollection("messages").insertOne(new Document("_id", id)
            .append("companyId", t.company()).append("channelId", t.channel())
            .append("conversationId", new ObjectId(conversation)).append("direction", direction)
            .append("provider", "MOCK").append("type", "TEXT").append("status", "RECEIVED")
            .append("content", new Document("text", "Teste"))
            .append("createdAt", Date.from(time)).append("updatedAt", Date.from(time)));
        return id.toHexString();
    }

    private JsonNode inbound(Tenant t, String external) {
        return call("/api/dev/whatsapp/mock/inbound", HttpMethod.POST,
            Map.of("companyId", t.company(), "channelId", t.channel(), "externalMessageId", external,
                "senderPhone", "+5581888888888", "senderName", "Cliente", "content", "Olá"), 200);
    }

    private JsonNode action(String id, String action, long actor, Long member, long version, int status) {
        Map<String, Object> body = new HashMap<>();
        body.put("actorMemberId", actor);
        body.put("expectedVersion", version);
        body.put("reason", "Teste da fila");
        if (member != null) body.put("memberId", member);
        return call(base() + "/" + id + "/" + action, HttpMethod.PATCH, body, status);
    }

    private JsonNode read(String id, long reader, String message, int status) {
        return call(base() + "/" + id + "/read", HttpMethod.POST,
            Map.of("memberId", reader, "throughMessageId", message), status);
    }

    private ResponseEntity<JsonNode> exchange(String path, HttpMethod method, Object body, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) headers.set("X-Dev-Api-Key", key);
        return http.exchange(URI.create(http.getRootUri() + path), method,
            new HttpEntity<>(body, headers), JsonNode.class);
    }

    private JsonNode call(String path, HttpMethod method, Object body, int expected) {
        var response = exchange(path, method, body, KEY);
        assertThat(response.getStatusCode().value()).as("%s %s: %s", method, path, response.getBody())
            .isEqualTo(expected);
        return response.getBody();
    }

    @Test
    void filtersIncludeLegacyStatesAndNeverLeakAnotherCompany() {
        String pending = conversation("Pendente", "PENDING", null, TIME);
        String legacyPending = conversation("Legado pendente", "OPEN", null, TIME);
        String human = conversation("Humano", "IN_PROGRESS", tenant.human(), TIME);
        String bot = conversation("Bot", "OPEN", tenant.bot(), TIME);
        String closed = conversation("Fechado", "CLOSED", tenant.human(), TIME);
        String closedUnassigned = conversation("Fechado sem membro", "CLOSED", null, TIME);
        conversation(other, "Outra empresa", "PENDING", null, TIME.plusSeconds(100));
        assertThat(ids(queue("status=PENDING"))).containsExactlyInAnyOrder(pending, legacyPending);
        assertThat(ids(queue("status=IN_PROGRESS"))).containsExactlyInAnyOrder(human, bot);
        assertThat(ids(queue("status=CLOSED"))).containsExactlyInAnyOrder(closed, closedUnassigned);
        assertThat(ids(queue("assignedMemberId=" + tenant.human()))).containsExactlyInAnyOrder(human, closed);
        assertThat(ids(queue("assignedMemberId=" + tenant.bot()))).containsExactly(bot);
        assertThat(ids(queue("unassigned=true"))).containsExactlyInAnyOrder(pending, legacyPending, closedUnassigned);
        assertThat(ids(queue("status=PENDING&unassigned=true"))).containsExactlyInAnyOrder(pending, legacyPending);
        assertThat(queue("")).hasSize(6);
        assertThat(get(base() + "/" + bot).path("status").asText()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void paginationIsStableWithTimestampTiesAndHandlesEmptyAndLastPages() {
        List<String> tied = new ArrayList<>();
        for (int i = 0; i < 5; i++) tied.add(conversation("Contato " + i, "PENDING", null, TIME));
        tied.sort(Comparator.reverseOrder());
        String newest = conversation("Recente", "PENDING", null, TIME.plusSeconds(1));
        List<String> expected = new ArrayList<>(List.of(newest));
        expected.addAll(tied);
        List<String> actual = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            JsonNode response = queue("paged=true&size=2&page=" + page);
            actual.addAll(ids(response.path("items")));
            assertThat(response.path("hasMore").asBoolean()).isEqualTo(page < 2);
            if (page < 2) assertThat(response.path("nextPage").asInt()).isEqualTo(page + 1);
            else assertThat(response.path("nextPage").isNull()).isTrue();
        }
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        JsonNode empty = queue("paged=true&size=2&page=3");
        assertThat(empty.path("items")).isEmpty();
        assertThat(empty.path("hasMore").asBoolean()).isFalse();
        assertThat(ids(queue("size=200"))).containsExactlyElementsOf(expected);
    }

    @Test
    void defaultPageSizeIsThirtyAndOtherTenantCanHaveEmptyQueue() {
        for (int i = 0; i < 31; i++) conversation("Contato " + i, "PENDING", null, TIME);
        JsonNode page = queue("paged=true");
        assertThat(page.path("items")).hasSize(30);
        assertThat(page.path("hasMore").asBoolean()).isTrue();
        assertThat(queue("paged=true&page=1").path("items")).hasSize(1);
        assertThat(queue("size=200")).hasSize(31);
        assertThat(get(base(other) + "?paged=true").path("items")).isEmpty();
    }

    @Test
    void searchMatchesNamesFormattedPhonesExternalIdsAndLiteralPunctuation() {
        String ana = conversation("Ana Comercial", "PENDING", null, TIME);
        String literal = conversation("Equipe ()", "PENDING", null, TIME);
        assertThat(ids(queue("search=" + encode("aNa")))).containsExactly(ana);
        assertThat(ids(queue("search=" + encode("+55 (81) 99999-9999"))))
            .containsExactlyInAnyOrder(ana, literal);
        assertThat(ids(queue("search=" + encode("external-Ana")))).containsExactly(ana);
        assertThat(ids(queue("search=" + ana))).containsExactly(ana);
        assertThat(ids(queue("search=" + encode("()")))).containsExactly(literal);
        assertThat(queue("search=" + encode(".*"))).isEmpty();
        assertThat(queue("search=" + encode("( - )"))).isEmpty();
        assertThat(queue("search=" + encode("   "))).hasSize(2);
    }

    @Test
    void invalidQueriesAndForeignOrInvalidReadersAreRejected() {
        for (String query : List.of("page=-1", "size=0", "size=201", "status=INVALID",
            "assignedMemberId=0", "readerMemberId=-1", "search=" + "a".repeat(101),
            "unassigned=true&assignedMemberId=" + tenant.human())) {
            call(base() + "?" + query, HttpMethod.GET, null, 400);
        }
        call(base() + "?assignedMemberId=" + other.human(), HttpMethod.GET, null, 404);
        call(base() + "?readerMemberId=" + other.human(), HttpMethod.GET, null, 404);
        call(base() + "?readerMemberId=" + tenant.bot(), HttpMethod.GET, null, 400);
        jdbc.update("UPDATE members SET status='INACTIVE' WHERE id=?", tenant.human());
        call(base() + "?readerMemberId=" + tenant.human(), HttpMethod.GET, null, 409);
    }

    @Test
    void realHttpGuardRequiresKeyAndRejectsCompanyOutsideServerContext() {
        assertThat(exchange(base(), HttpMethod.GET, null, null).getStatusCode().value()).isEqualTo(401);
        assertThat(exchange(base(), HttpMethod.GET, null, "invalid").getStatusCode().value()).isEqualTo(401);
        doReturn(false).when(access).allows(anyLong());
        doReturn(true).when(access).allows(tenant.company());
        get(base());
        call(base(other), HttpMethod.GET, null, 403);
        call("/api/dev/whatsapp/mock/inbound", HttpMethod.POST,
            Map.of("companyId", other.company(), "channelId", other.channel(), "externalMessageId", "denied",
                "senderPhone", "+5581888888888", "content", "Olá"), 403);
    }

    @Test
    void directConversationMessageAndReadAccessRemainTenantScoped() {
        String id = conversation("Contato", "PENDING", null, TIME);
        String msg = message(tenant, id, "INBOUND", TIME);
        call(base(other) + "/" + id, HttpMethod.GET, null, 404);
        call(base(other) + "/" + id + "/messages", HttpMethod.GET, null, 404);
        call(base(other) + "/" + id + "/read", HttpMethod.POST,
            Map.of("memberId", other.human(), "throughMessageId", msg), 404);
        call(base() + "/invalid", HttpMethod.GET, null, 400);
        call(base() + "/" + new ObjectId(), HttpMethod.GET, null, 404);
    }

    @Test
    void managementPersistsTransitionsAuditsAndIdempotency() {
        String id = conversation("Contato", "PENDING", null, TIME);
        JsonNode assumed = action(id, "assume", tenant.human(), null, 0, 200);
        assertThat(assumed.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(assumed.path("assignedMemberId").asLong()).isEqualTo(tenant.human());
        JsonNode repeat = action(id, "assume", tenant.human(), null, 0, 200);
        assertThat(repeat.path("version")).isEqualTo(assumed.path("version"));
        action(id, "assign", tenant.human(), tenant.bot(), 1, 409);
        action(id, "transfer", tenant.human(), other.bot(), 1, 404);
        action(id, "transfer", tenant.human(), tenant.bot(), 0, 409);
        JsonNode transferred = action(id, "transfer", tenant.human(), tenant.bot(), 1, 200);
        assertThat(transferred.path("assignedMemberId").asLong()).isEqualTo(tenant.bot());
        JsonNode closed = action(id, "close", tenant.human(), null, 2, 200);
        assertThat(closed.path("status").asText()).isEqualTo("CLOSED");
        assertThat(closed.path("closedAt").isNull()).isFalse();
        action(id, "assign", tenant.human(), tenant.human(), 3, 409);
        call(base() + "/" + id + "/messages", HttpMethod.POST,
            Map.of("channelId", tenant.channel(), "memberId", tenant.human(), "content", "Bloqueado"), 409);
        JsonNode reopened = action(id, "reopen", tenant.human(), null, 3, 200);
        assertThat(reopened.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(reopened.path("closedAt").isNull()).isTrue();
        assertThat(reopened.path("assignedMemberId").asLong()).isEqualTo(tenant.bot());
        action(id, "close", tenant.human(), null, 4, 200);
        jdbc.update("UPDATE members SET status='INACTIVE' WHERE id=?", tenant.bot());
        JsonNode unassigned = action(id, "reopen", tenant.human(), null, 5, 200);
        assertThat(unassigned.path("status").asText()).isEqualTo("PENDING");
        assertThat(unassigned.path("assignedMemberId").isNull()).isTrue();
        assertThat(ids(queue("status=PENDING"))).containsExactly(id);
        assertThat(get(base() + "/" + id + "/events")).hasSize(6);
        assertThat(mongo.getCollection("conversations").find(new Document("_id", new ObjectId(id)))
            .first().getList("pendingActivities", Document.class)).isEmpty();
    }

    @Test
    void concurrentAssignmentsHaveOnlyOneWinnerAndOneAuditEvent() {
        String id = conversation("Concorrente", "PENDING", null, TIME);
        var requestA = Map.of("actorMemberId", tenant.human(), "memberId", tenant.human(), "expectedVersion", 0);
        var requestB = Map.of("actorMemberId", tenant.human(), "memberId", tenant.bot(), "expectedVersion", 0);
        var a = CompletableFuture.supplyAsync(() -> exchange(base() + "/" + id + "/assign", HttpMethod.PATCH, requestA, KEY));
        var b = CompletableFuture.supplyAsync(() -> exchange(base() + "/" + id + "/assign", HttpMethod.PATCH, requestB, KEY));
        assertThat(List.of(a.join().getStatusCode().value(), b.join().getStatusCode().value()))
            .containsExactlyInAnyOrder(200, 409);
        assertThat(get(base() + "/" + id).path("version").asLong()).isEqualTo(1);
        assertThat(get(base() + "/" + id + "/events")).hasSize(1);
    }

    @Test
    void newInboundReopensClosedConversationAndDuplicateDoesNotReopenAgain() {
        JsonNode first = inbound(tenant, "first-" + UUID.randomUUID());
        String id = first.path("conversationId").asText();
        long version = get(base() + "/" + id).path("version").asLong();
        JsonNode assigned = action(id, "assign", tenant.human(), tenant.bot(), version, 200);
        action(id, "close", tenant.human(), null, assigned.path("version").asLong(), 200);
        String external = "reopen-" + UUID.randomUUID();
        JsonNode next = inbound(tenant, external);
        assertThat(inbound(tenant, external).path("id")).isEqualTo(next.path("id"));
        JsonNode current = get(base() + "/" + id);
        assertThat(current.path("status").asText()).isEqualTo("PENDING");
        assertThat(current.path("assignedMemberId").isNull()).isTrue();
        assertThat(current.path("closedAt").isNull()).isTrue();
        List<String> events = StreamSupport.stream(get(base() + "/" + id + "/events").spliterator(), false)
            .map(row -> row.path("event").asText()).toList();
        assertThat(Collections.frequency(events, "CONVERSATION_AUTO_REOPENED")).isEqualTo(1);
        assertThat(get(base() + "/" + id + "/messages")).hasSize(2);
    }

    @Test
    void unreadBoundaryUsesTimestampAndIdIsMonotonicAndExcludesOutboundAndOtherTenants() {
        String id = conversation("Leitura", "PENDING", null, TIME);
        String older = message(tenant, id, "inbound", TIME.minusSeconds(1));
        String first = message(tenant, id, "INBOUND", TIME);
        String second = message(tenant, id, "INBOUND", TIME);
        message(tenant, id, "OUTBOUND", TIME.plusSeconds(1));
        message(other, id, "INBOUND", TIME.plusSeconds(2));
        assertThat(queue("readerMemberId=" + tenant.human()).get(0).path("unreadCount").asLong()).isEqualTo(3);
        assertThat(queue("").get(0).path("unreadCount").isNull()).isTrue();
        assertThat(read(id, tenant.human(), first, 200).path("unreadCount").asLong()).isEqualTo(1);
        assertThat(read(id, tenant.human(), older, 200).path("throughMessageId").asText()).isEqualTo(first);
        read(id, tenant.bot(), second, 400);
        read(id, other.human(), second, 404);
        String unrelated = conversation("Outro contato", "PENDING", null, TIME);
        String unrelatedMessage = message(tenant, unrelated, "INBOUND", TIME);
        read(id, tenant.human(), unrelatedMessage, 404);
        read(id, tenant.human(), "invalid", 400);
        var a = CompletableFuture.supplyAsync(() -> read(id, tenant.human(), first, 200));
        var b = CompletableFuture.supplyAsync(() -> read(id, tenant.human(), second, 200));
        a.join(); b.join();
        assertThat(read(id, tenant.human(), second, 200).path("unreadCount").asLong()).isZero();
        assertThat(mongo.getCollection("conversation_reads").countDocuments(new Document("companyId", tenant.company())
            .append("conversationId", new ObjectId(id)).append("memberId", tenant.human()))).isEqualTo(1);
        message(tenant, id, "INBOUND", TIME.plusSeconds(3));
        JsonNode row = StreamSupport.stream(queue("readerMemberId=" + tenant.human()).spliterator(), false)
            .filter(item -> item.path("id").asText().equals(id)).findFirst().orElseThrow();
        assertThat(row.path("unreadCount").asLong()).isEqualTo(1);
    }

    @Test
    void messageCursorWalksEveryMessageWithTimestampTiesWithoutDuplicates() {
        String id = conversation("Histórico", "PENDING", null, TIME);
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 65; i++) expected.add(message(tenant, id, "INBOUND", TIME));
        String path = base() + "/" + id + "/messages";
        JsonNode page = get(path + "?limit=30");
        List<String> received = new ArrayList<>(ids(page.path("items")));
        assertThat(page.path("items")).hasSize(30);
        while (page.path("hasMore").asBoolean()) {
            page = get(path + "?limit=30&before=" + encode(page.path("nextCursor").asText()));
            received.addAll(ids(page.path("items")));
        }
        assertThat(received).hasSize(65).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
        assertThat(page.path("items")).hasSize(5);
        assertThat(page.path("nextCursor").isNull()).isTrue();
        call(path + "?limit=0", HttpMethod.GET, null, 400);
        call(path + "?limit=101", HttpMethod.GET, null, 400);
        call(path + "?limit=30&before=invalid", HttpMethod.GET, null, 400);
    }

    @Test
    void transfersSupportHumanHumanHumanBotBotBotAndBotHumanWithAuditedMembers() {
        String prefix = "/api/companies/" + tenant.company();
        long user = call(prefix + "/users", HttpMethod.POST, Map.of("name", "Bruno"), 201).path("id").asLong();
        long human = call(prefix + "/members", HttpMethod.POST,
            Map.of("name", "Bruno", "type", "HUMAN", "userId", user), 201).path("id").asLong();
        long bot = call(prefix + "/members", HttpMethod.POST,
            Map.of("name", "Bot 2", "type", "BOT"), 201).path("id").asLong();
        String id = conversation("Transferências", "PENDING", null, TIME);
        action(id, "assign", tenant.human(), tenant.human(), 0, 200);
        long previous = tenant.human();
        long version = 1;
        for (long next : List.of(human, tenant.bot(), bot, tenant.human())) {
            JsonNode result = action(id, "transfer", tenant.human(), next, version++, 200);
            assertThat(result.path("assignedMemberId").asLong()).isEqualTo(next);
            assertThat(result.path("status").asText()).isEqualTo("IN_PROGRESS");
            JsonNode events = get(base() + "/" + id + "/events");
            JsonNode event = events.get(events.size() - 1);
            assertThat(event.path("previousMemberId").asLong()).isEqualTo(previous);
            assertThat(event.path("newMemberId").asLong()).isEqualTo(next);
            previous = next;
        }
        assertThat(get(base() + "/" + id + "/events")).hasSize(5);
        action(id, "transfer", tenant.human(), tenant.human(), version, 200);
        assertThat(get(base() + "/" + id + "/events")).hasSize(5);
        jdbc.update("UPDATE members SET status='INACTIVE' WHERE id=?", bot);
        action(id, "transfer", tenant.human(), bot, version, 409);
    }

    @Test
    void readStateIsIndependentPerHumanAndDoesNotChangeMessageDeliveryStatus() {
        String prefix = "/api/companies/" + tenant.company();
        long user = call(prefix + "/users", HttpMethod.POST, Map.of("name", "Bruno"), 201).path("id").asLong();
        long human = call(prefix + "/members", HttpMethod.POST,
            Map.of("name", "Bruno", "type", "HUMAN", "userId", user), 201).path("id").asLong();
        String id = conversation("Leitura individual", "PENDING", null, TIME);
        String msg = message(tenant, id, "INBOUND", TIME);
        read(id, tenant.human(), msg, 200);
        assertThat(queue("readerMemberId=" + tenant.human()).get(0).path("unreadCount").asLong()).isZero();
        assertThat(queue("readerMemberId=" + human).get(0).path("unreadCount").asLong()).isEqualTo(1);
        assertThat(get(base() + "/" + id + "/messages").get(0).path("status").asText()).isEqualTo("RECEIVED");
    }
}
