package com.wxn.chatbot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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

/** Opt-in HTTP/service/repository tests against real PostgreSQL and MongoDB. */
@EnabledIfEnvironmentVariable(named = "WXN_INTEGRATION_TEST", matches = "true")
@ActiveProfiles({ "dev", "legacy" })
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "wxn.dev.api-key=integration-test-key-minimum-32-characters"
)
class ConversationQueueIntegrationTest {

    private static final String KEY = "integration-test-key-minimum-32-characters";
    private static final Instant TIME = Instant.parse("2026-01-01T12:00:00Z");

    @Autowired
    TestRestTemplate http;

    @Autowired
    MongoTemplate mongo;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    com.wxn.chatbot.notification.service.NotificationService notifications;

    @Autowired
    com.wxn.chatbot.notification.repository.NotificationRepository notificationRepository;

    private Fixture fixture;

    record Fixture(long human, long bot, long channel, long department) {}

    private String cookie;
    private String csrf;
    private long administrator;

    @Autowired
    com.wxn.chatbot.user.service.UserService accountUsers;

    void authenticateAdministrator() {
        var request = LocalFlowIntegrationTest.account(
            "Administrador de verificação",
            com.wxn.chatbot.user.entity.UserRole.ADMIN,
            List.of()
        );
        administrator = accountUsers.create(request).memberId();
        authenticate(request);
    }

    private void authenticate(com.wxn.chatbot.user.dto.UserDtos.Create request) {
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

    private HttpHeaders loginHeaders(com.wxn.chatbot.user.dto.UserDtos.Create account) {
        var headers = new HttpHeaders();
        headers.set("X-Dev-Api-Key", KEY);
        var session = http.exchange(
            "/api/auth/session",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class
        );
        headers.set(
            HttpHeaders.COOKIE,
            session.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0]
        );
        headers.set("X-CSRF-Token", session.getBody().path("csrfToken").asText());
        var login = http.exchange(
            "/api/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("username", account.username(), "password", account.password()), headers),
            JsonNode.class
        );
        headers.set(HttpHeaders.COOKIE, login.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0]);
        headers.set("X-CSRF-Token", login.getBody().path("csrfToken").asText());
        return headers;
    }

    @Test
    void twoAttendantsRaceForAvailableAlertAndLoserIsRejected() {
        var first = LocalFlowIntegrationTest.account(
            "Claim um",
            com.wxn.chatbot.user.entity.UserRole.ATTENDANT,
            List.of(fixture.department())
        );
        var second = LocalFlowIntegrationTest.account(
            "Claim dois",
            com.wxn.chatbot.user.entity.UserRole.ATTENDANT,
            List.of(fixture.department())
        );
        var firstUser = accountUsers.create(first);
        var secondUser = accountUsers.create(second);
        long firstMember = firstUser.memberId();
        long secondMember = secondUser.memberId();
        String conversationId = conversation("Fila de handoff", "PENDING", null, TIME);
        notifications.offerConversation(
            conversationId,
            "race:" + conversationId,
            List.of(firstUser.id(), secondUser.id())
        );
        var firstNote = notificationRepository
            .findByUserIdOrderByCreatedAtDescIdDesc(
                firstUser.id(),
                org.springframework.data.domain.PageRequest.of(0, 2)
            )
            .stream()
            .filter(n -> "CONVERSATION_AVAILABLE".equals(n.getType()))
            .findFirst()
            .orElseThrow();
        var secondNote = notificationRepository
            .findByUserIdOrderByCreatedAtDescIdDesc(
                secondUser.id(),
                org.springframework.data.domain.PageRequest.of(0, 2)
            )
            .stream()
            .filter(n -> "CONVERSATION_AVAILABLE".equals(n.getType()))
            .findFirst()
            .orElseThrow();
        var firstRequest = new HttpEntity<Void>(loginHeaders(first));
        var secondRequest = new HttpEntity<Void>(loginHeaders(second));
        var one = CompletableFuture.supplyAsync(() ->
            http.exchange(
                "/api/notifications/" + firstNote.getId() + "/claim",
                HttpMethod.POST,
                firstRequest,
                JsonNode.class
            )
        );
        var two = CompletableFuture.supplyAsync(() ->
            http.exchange(
                "/api/notifications/" + secondNote.getId() + "/claim",
                HttpMethod.POST,
                secondRequest,
                JsonNode.class
            )
        );
        var a = one.join();
        var b = two.join();
        assertThat(List.of(a.getStatusCode().value(), b.getStatusCode().value()))
            .contains(200)
            .containsAnyOf(409, 404);
        var assigned = mongo
            .getCollection("conversations")
            .find(new Document("_id", new ObjectId(conversationId)))
            .first();
        assertThat(assigned.getLong("assignedMemberId")).isIn(firstMember, secondMember);
        assertThat(
            notificationRepository.findByResourceTypeAndResourceIdAndType(
                "CONVERSATION",
                conversationId,
                "CONVERSATION_AVAILABLE"
            )
        ).allMatch(n -> n.getReadAt() != null);
    }

    @BeforeEach
    void fixtures() {
        authenticateAdministrator();
        for (String collection : List.of(
            "conversations",
            "messages",
            "conversation_reads",
            "conversation_events",
            "message_logs"
        )) {
            mongo.getCollection(collection).deleteMany(new Document());
        }
        fixture = fixture();
    }

    @Test
    void attendantSeesOnlyAssignedChatsWhileAdministratorCanSeeAll() {
        var account = LocalFlowIntegrationTest.account(
            "Atendente restrito",
            com.wxn.chatbot.user.entity.UserRole.ATTENDANT,
            List.of(fixture.department())
        );
        long memberId = accountUsers.create(account).memberId();
        String assigned = conversation("Minha conversa", "IN_PROGRESS", memberId, TIME);
        String other = conversation("Outra conversa", "IN_PROGRESS", fixture.human(), TIME);
        String waiting = conversation("Sem responsável", "PENDING", null, TIME);
        assertThat(ids(queue(""))).containsExactlyInAnyOrder(assigned, other, waiting);
        assertThat(ids(queue("assignedMemberId=" + administrator))).containsExactly(other);

        var headers = new HttpHeaders();
        headers.set("X-Dev-Api-Key", KEY);
        var session = http.exchange(
            "/api/auth/session",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class
        );
        headers.set(
            HttpHeaders.COOKIE,
            session.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0]
        );
        headers.set("X-CSRF-Token", session.getBody().path("csrfToken").asText());
        var login = http.exchange(
            "/api/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("username", account.username(), "password", account.password()), headers),
            JsonNode.class
        );
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        headers.set(HttpHeaders.COOKIE, login.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0]);
        var attendant = new HttpEntity<Void>(headers);
        var visible = http.exchange(base(), HttpMethod.GET, attendant, JsonNode.class);
        assertThat(visible.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ids(visible.getBody())).containsExactly(assigned);
        var paged = http.exchange(
            base() + "?paged=true&assignedMemberId=" + fixture.human(),
            HttpMethod.GET,
            attendant,
            JsonNode.class
        );
        assertThat(paged.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ids(paged.getBody().path("items"))).containsExactly(assigned);
        headers.set("X-CSRF-Token", login.getBody().path("csrfToken").asText());
        var wait = new HttpEntity<>(Map.of("actorMemberId", memberId, "expectedVersion", 0), headers);
        var blocked = http.exchange(
            base() + "/" + other + "/status?status=WAITING_CUSTOMER",
            HttpMethod.PATCH,
            wait,
            JsonNode.class
        );
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var accepted = http.exchange(
            base() + "/" + assigned + "/status?status=WAITING_CUSTOMER",
            HttpMethod.PATCH,
            wait,
            JsonNode.class
        );
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getBody().path("status").asText()).isEqualTo("WAITING_CUSTOMER");
        assertThat(ids(queue("status=WAITING_CUSTOMER"))).containsExactly(assigned);

        String path = base() + "/" + assigned;
        long version = accepted.getBody().path("version").asLong();
        call(
            path + "/status?status=RESOLVED",
            HttpMethod.PATCH,
            Map.of("actorMemberId", administrator, "expectedVersion", version),
            403
        );
        var current = new HttpEntity<>(
            Map.of("actorMemberId", memberId, "expectedVersion", version),
            headers
        );
        assertThat(
            http
                .exchange(path + "/status?status=PENDING", HttpMethod.PATCH, current, JsonNode.class)
                .getStatusCode()
        ).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
            http
                .exchange(path + "/status?status=CANCELLED", HttpMethod.PATCH, current, JsonNode.class)
                .getStatusCode()
        ).isEqualTo(HttpStatus.BAD_REQUEST);
        var resolved = http.exchange(
            path + "/status?status=RESOLVED",
            HttpMethod.PATCH,
            current,
            JsonNode.class
        );
        assertThat(resolved.getStatusCode()).isEqualTo(HttpStatus.OK);
        var finalVersion = new HttpEntity<>(
            Map.of("actorMemberId", memberId, "expectedVersion", resolved.getBody().path("version").asLong()),
            headers
        );
        assertThat(
            http
                .exchange(path + "/status?status=IN_PROGRESS", HttpMethod.PATCH, finalVersion, JsonNode.class)
                .getStatusCode()
        ).isEqualTo(HttpStatus.CONFLICT);
        var reopened = http.exchange(path + "/reopen", HttpMethod.PATCH, finalVersion, JsonNode.class);
        assertThat(reopened.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reopened.getBody().path("status").asText()).isEqualTo("IN_PROGRESS");

        assertThat(
            http
                .exchange(base() + "/" + other + "/messages", HttpMethod.GET, attendant, JsonNode.class)
                .getStatusCode()
        ).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
            http.exchange(base() + "/" + waiting, HttpMethod.GET, attendant, JsonNode.class).getStatusCode()
        ).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private Fixture fixture() {
        String base = "/api";
        long department = call(
            base + "/departments",
            HttpMethod.POST,
            Map.of("name", "Suporte", "color", "#2563EB"),
            201
        )
            .path("id")
            .asLong();
        long human = administrator;
        long bot = call(base + "/members", HttpMethod.POST, Map.of("name", "Bot", "type", "BOT"), 201)
            .path("id")
            .asLong();
        long channel = call(
            base + "/whatsapp/channels",
            HttpMethod.POST,
            Map.of("name", "Mock", "provider", "MOCK", "phoneNumber", "+14155238886"),
            201
        )
            .path("id")
            .asLong();
        return new Fixture(human, bot, channel, department);
    }

    private String base() {
        return "/api/conversations";
    }

    private JsonNode get(String path) {
        return call(path, HttpMethod.GET, null, 200);
    }

    private JsonNode queue(String query) {
        return get(base() + "?" + query);
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private static List<String> ids(JsonNode rows) {
        return StreamSupport.stream(rows.spliterator(), false)
            .map(row -> row.path("id").asText())
            .toList();
    }

    private String conversation(Fixture t, String name, String status, Long member, Instant time) {
        ObjectId id = new ObjectId();
        mongo.getCollection("conversations").insertOne(
            new Document("_id", id)
                .append("channelId", t.channel())
                .append("conversationKey", "fixture:" + id.toHexString())
                .append("provider", "MOCK")
                .append("conversationType", "INDIVIDUAL")
                .append("contactPhone", "+5581999999999")
                .append("displayName", name)
                .append("externalChatId", "external-" + name)
                .append("status", status)
                .append("assignedMemberId", member)
                .append("version", 0L)
                .append("createdAt", Date.from(time))
                .append("updatedAt", Date.from(time))
        );
        return id.toHexString();
    }

    private String conversation(String name, String status, Long member, Instant time) {
        return conversation(fixture, name, status, member, time);
    }

    private String message(Fixture t, String conversation, String direction, Instant time) {
        ObjectId id = new ObjectId();
        mongo
            .getCollection("messages")
            .insertOne(
                new Document("_id", id)
                    .append("channelId", t.channel())
                    .append("conversationId", new ObjectId(conversation))
                    .append("direction", direction)
                    .append("provider", "MOCK")
                    .append("type", "TEXT")
                    .append("status", "RECEIVED")
                    .append("content", new Document("text", "Teste"))
                    .append("createdAt", Date.from(time))
                    .append("updatedAt", Date.from(time))
            );
        return id.toHexString();
    }

    private JsonNode inbound(Fixture t, String external) {
        return call(
            "/api/dev/whatsapp/mock/inbound",
            HttpMethod.POST,
            Map.of(
                "channelId",
                t.channel(),
                "externalMessageId",
                external,
                "senderPhone",
                "+5581888888888",
                "senderName",
                "Cliente",
                "content",
                "Olá"
            ),
            200
        );
    }

    private JsonNode action(String id, String action, long actor, Long member, long version, int status) {
        Map<String, Object> body = new HashMap<>();
        body.put("actorMemberId", actor);
        body.put("expectedVersion", version);
        body.put("reason", "Teste da fila");
        if (member != null) {
            body.put("memberId", member);
        }
        return call(base() + "/" + id + "/" + action, HttpMethod.PATCH, body, status);
    }

    private JsonNode read(String id, long reader, String message, int status) {
        return call(
            base() + "/" + id + "/read",
            HttpMethod.POST,
            Map.of("memberId", reader, "throughMessageId", message),
            status
        );
    }

    private ResponseEntity<JsonNode> exchange(String path, HttpMethod method, Object body, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            headers.set("X-Dev-Api-Key", key);
        }
        headers.set(HttpHeaders.COOKIE, cookie);
        headers.set("X-CSRF-Token", csrf);
        return http.exchange(
            URI.create(http.getRootUri() + path),
            method,
            new HttpEntity<>(body, headers),
            JsonNode.class
        );
    }

    private JsonNode call(String path, HttpMethod method, Object body, int expected) {
        var response = exchange(path, method, body, KEY);
        assertThat(response.getStatusCode().value())
            .as("%s %s: %s", method, path, response.getBody())
            .isEqualTo(expected);
        return response.getBody();
    }

    @Test
    void dashboardChartsCountEntriesResolutionCyclesAndWaitingConversations() {
        String path = "/api/admin/kanban/metrics";
        var zone = java.time.ZoneId.of(get(path).path("timezone").asText());
        Instant now = Instant.now();
        var yesterday = now.atZone(zone).toLocalDate().minusDays(1);
        Instant openedAt = yesterday.atTime(12, 0).atZone(zone).toInstant();
        String active = conversation("Resposta pendente", "IN_PROGRESS", fixture.human(), openedAt);
        String resolved = conversation("Resolvido", "RESOLVED", fixture.human(), openedAt);
        var rows = mongo.getCollection("conversations");
        rows.updateOne(
            new Document("_id", new ObjectId(active)),
            new Document("$set", new Document("waitingForAgentSince", Date.from(now.minusSeconds(4200))))
        );
        rows.updateOne(
            new Document("_id", new ObjectId(resolved)),
            new Document(
                "$set",
                new Document("resolvedAt", Date.from(now))
                    .append(
                        "resolutionCycles",
                        List.of(
                            new Document("startedAt", Date.from(openedAt)).append(
                                "resolvedAt",
                                Date.from(now)
                            )
                        )
                    )
                    .append("waitingForAgentSince", Date.from(now.minusSeconds(4200)))
            )
        );
        var metrics = get(path);
        var volume = metrics.path("dailyVolume");
        assertThat(volume).hasSize(7);
        assertThat(volume.get(5).path("date").asText()).isEqualTo(yesterday.toString());
        assertThat(volume.get(5).path("opened").asLong()).isEqualTo(2);
        assertThat(volume.get(6).path("resolved").asLong()).isEqualTo(1);
        assertThat(metrics.path("resolvedToday").asLong()).isEqualTo(1);
        assertThat(metrics.path("replyAging").path("over60").asLong()).isEqualTo(1);
        assertThat(metrics.path("replyAging").path("under15").asLong()).isZero();
    }

    @Test
    void metricsShowAtMostThreeOldestOpenConversationsAwaitingReply() {
        Instant now = Instant.now();
        var rows = mongo.getCollection("conversations");
        var expected = new ArrayList<String>();
        for (int position = 0; position < 5; position++) {
            String status = position == 0 ? "CLOSED" : "IN_PROGRESS";
            String id = conversation("Espera " + position, status, fixture.human(), now.minusSeconds(9000));
            rows.updateOne(
                new Document("_id", new ObjectId(id)),
                new Document(
                    "$set",
                    new Document("waitingForAgentSince", Date.from(now.minusSeconds(7200 - position * 600L)))
                )
            );
            if (position > 0 && position < 4) {
                expected.add(id);
            }
        }
        var cards = get("/api/admin/kanban/metrics").path("longestWaiting");
        assertThat(
            StreamSupport.stream(cards.spliterator(), false).map(card ->
                card.path("conversation").path("id").asText()
            )
        ).containsExactlyElementsOf(expected);
        assertThat(cards).allMatch(card -> card.path("sla").isTextual());
    }

    @Test
    void filtersIncludeConversationStates() {
        String pending = conversation("Pendente", "PENDING", null, TIME);
        String legacyPending = conversation("Legado pendente", "OPEN", null, TIME);
        String human = conversation("Humano", "IN_PROGRESS", fixture.human(), TIME);
        String bot = conversation("Bot", "OPEN", fixture.bot(), TIME);
        String closed = conversation("Fechado", "CLOSED", fixture.human(), TIME);
        String closedUnassigned = conversation("Fechado sem membro", "CLOSED", null, TIME);
        String waiting = conversation("Aguardando cliente", "WAITING_CUSTOMER", fixture.bot(), TIME);
        String cancelled = conversation("Inatividade", "CANCELLED", fixture.bot(), TIME);
        assertThat(ids(queue("status=PENDING"))).containsExactlyInAnyOrder(pending, legacyPending);
        assertThat(ids(queue("status=IN_PROGRESS"))).containsExactlyInAnyOrder(human, bot, waiting);
        assertThat(ids(queue("status=CLOSED"))).containsExactlyInAnyOrder(
            closed,
            closedUnassigned,
            cancelled
        );
        assertThat(ids(queue("assignedMemberId=" + fixture.human()))).containsExactlyInAnyOrder(
            human,
            closed
        );
        assertThat(ids(queue("assignedMemberId=" + fixture.bot()))).containsExactlyInAnyOrder(
            bot,
            waiting,
            cancelled
        );
        assertThat(ids(queue("unassigned=true"))).containsExactlyInAnyOrder(
            pending,
            legacyPending,
            closedUnassigned
        );
        assertThat(ids(queue("status=PENDING&unassigned=true"))).containsExactlyInAnyOrder(
            pending,
            legacyPending
        );
        assertThat(queue("")).hasSize(8);
        var activeCards = get("/api/admin/kanban/conversations?status=IN_PROGRESS").path("items");
        assertThat(activeCards).anyMatch(card ->
            card.path("conversation").path("id").asText().equals(waiting)
        );
        var closedCards = get("/api/admin/kanban/conversations?status=CLOSED").path("items");
        assertThat(closedCards).anyMatch(card ->
            card.path("conversation").path("id").asText().equals(cancelled)
        );
        assertThat(
            get(base() + "/" + bot)
                .path("status")
                .asText()
        ).isEqualTo("IN_PROGRESS");
    }

    @Test
    void paginationIsStableWithTimestampTiesAndHandlesEmptyAndLastPages() {
        List<String> tied = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tied.add(conversation("Contato " + i, "PENDING", null, TIME));
        }
        tied.sort(Comparator.reverseOrder());
        String newest = conversation("Recente", "PENDING", null, TIME.plusSeconds(1));
        List<String> expected = new ArrayList<>(List.of(newest));
        expected.addAll(tied);
        List<String> actual = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            JsonNode response = queue("paged=true&size=2&page=" + page);
            actual.addAll(ids(response.path("items")));
            assertThat(response.path("hasMore").asBoolean()).isEqualTo(page < 2);
            if (page < 2) {
                assertThat(response.path("nextPage").asInt()).isEqualTo(page + 1);
            } else {
                assertThat(response.path("nextPage").isNull()).isTrue();
            }
        }
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        JsonNode empty = queue("paged=true&size=2&page=3");
        assertThat(empty.path("items")).isEmpty();
        assertThat(empty.path("hasMore").asBoolean()).isFalse();
        assertThat(ids(queue("size=200"))).containsExactlyElementsOf(expected);
    }

    @Test
    void defaultPageSizeIsThirty() {
        for (int i = 0; i < 31; i++) {
            conversation("Contato " + i, "PENDING", null, TIME);
        }
        JsonNode page = queue("paged=true");
        assertThat(page.path("items")).hasSize(30);
        assertThat(page.path("hasMore").asBoolean()).isTrue();
        assertThat(queue("paged=true&page=1").path("items")).hasSize(1);
        assertThat(queue("size=200")).hasSize(31);
    }

    @Test
    void searchMatchesNamesFormattedPhonesExternalIdsAndLiteralPunctuation() {
        String ana = conversation("Ana Comercial", "PENDING", null, TIME);
        String literal = conversation("Equipe ()", "PENDING", null, TIME);
        assertThat(ids(queue("search=" + encode("aNa")))).containsExactly(ana);
        assertThat(ids(queue("search=" + encode("+55 (81) 99999-9999")))).containsExactlyInAnyOrder(
            ana,
            literal
        );
        assertThat(ids(queue("search=" + encode("external-Ana")))).containsExactly(ana);
        assertThat(ids(queue("search=" + ana))).containsExactly(ana);
        assertThat(ids(queue("search=" + encode("()")))).containsExactly(literal);
        assertThat(queue("search=" + encode(".*"))).isEmpty();
        assertThat(queue("search=" + encode("( - )"))).isEmpty();
        assertThat(queue("search=" + encode("   "))).hasSize(2);
    }

    @Test
    void invalidQueriesAndReadersAreRejected() {
        for (String query : List.of(
            "page=-1",
            "size=0",
            "size=201",
            "status=INVALID",
            "assignedMemberId=0",
            "readerMemberId=-1",
            "search=" + "a".repeat(101),
            "unassigned=true&assignedMemberId=" + fixture.human()
        )) {
            call(base() + "?" + query, HttpMethod.GET, null, 400);
        }
        call(base() + "?assignedMemberId=" + Long.MAX_VALUE, HttpMethod.GET, null, 404);
        call(base() + "?readerMemberId=" + Long.MAX_VALUE, HttpMethod.GET, null, 403);
        call(base() + "?readerMemberId=" + fixture.bot(), HttpMethod.GET, null, 403);
        jdbc.update("UPDATE members SET status='INACTIVE' WHERE id=?", fixture.human());
        call(base() + "?readerMemberId=" + fixture.human(), HttpMethod.GET, null, 409);
    }

    @Test
    void realHttpGuardRequiresKey() {
        assertThat(exchange(base(), HttpMethod.GET, null, null).getStatusCode().value()).isEqualTo(401);
        assertThat(exchange(base(), HttpMethod.GET, null, "invalid").getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void invalidOrMissingConversationsAreRejected() {
        call(base() + "/invalid", HttpMethod.GET, null, 400);
        call(base() + "/" + new ObjectId(), HttpMethod.GET, null, 404);
    }

    @Test
    void managementPersistsTransitionsAuditsAndIdempotency() {
        String id = conversation("Contato", "PENDING", null, TIME);
        JsonNode assumed = action(id, "assume", fixture.human(), null, 0, 200);
        assertThat(assumed.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(assumed.path("assignedMemberId").asLong()).isEqualTo(fixture.human());
        JsonNode repeat = action(id, "assume", fixture.human(), null, 0, 200);
        assertThat(repeat.path("version")).isEqualTo(assumed.path("version"));
        action(id, "assign", fixture.human(), fixture.bot(), 1, 409);
        action(id, "transfer", fixture.human(), Long.MAX_VALUE, 1, 404);
        action(id, "transfer", fixture.human(), fixture.bot(), 0, 409);
        JsonNode transferred = action(id, "transfer", fixture.human(), fixture.bot(), 1, 200);
        assertThat(transferred.path("assignedMemberId").asLong()).isEqualTo(fixture.bot());
        action(id, "close", fixture.human(), null, 2, 403);
        action(id, "transfer", fixture.human(), fixture.human(), 2, 200);
        JsonNode closed = action(id, "close", fixture.human(), null, 3, 200);
        assertThat(closed.path("status").asText()).isEqualTo("CLOSED");
        assertThat(closed.path("closedAt").isNull()).isFalse();
        action(id, "assign", fixture.human(), fixture.human(), 4, 409);
        call(
            base() + "/" + id + "/messages",
            HttpMethod.POST,
            Map.of("content", "Bloqueado", "operationId", UUID.randomUUID().toString()),
            409
        );
        JsonNode reopened = action(id, "reopen", fixture.human(), null, 4, 200);
        assertThat(reopened.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(reopened.path("closedAt").isNull()).isTrue();
        assertThat(reopened.path("assignedMemberId").asLong()).isEqualTo(fixture.human());
        action(id, "close", fixture.human(), null, 5, 200);
        assertThat(get(base() + "/" + id + "/events")).hasSize(6);
        assertThat(
            mongo
                .getCollection("conversations")
                .find(new Document("_id", new ObjectId(id)))
                .first()
                .getList("pendingActivities", Document.class)
        ).isEmpty();
    }

    @Test
    void concurrentAssignmentsHaveOnlyOneWinnerAndOneAuditEvent() {
        String id = conversation("Concorrente", "PENDING", null, TIME);
        var requestA = Map.of(
            "actorMemberId",
            fixture.human(),
            "memberId",
            fixture.human(),
            "expectedVersion",
            0
        );
        var requestB = Map.of(
            "actorMemberId",
            fixture.human(),
            "memberId",
            fixture.bot(),
            "expectedVersion",
            0
        );
        var a = CompletableFuture.supplyAsync(() ->
            exchange(base() + "/" + id + "/assign", HttpMethod.PATCH, requestA, KEY)
        );
        var b = CompletableFuture.supplyAsync(() ->
            exchange(base() + "/" + id + "/assign", HttpMethod.PATCH, requestB, KEY)
        );
        assertThat(
            List.of(a.join().getStatusCode().value(), b.join().getStatusCode().value())
        ).containsExactlyInAnyOrder(200, 409);
        assertThat(
            get(base() + "/" + id)
                .path("version")
                .asLong()
        ).isEqualTo(1);
        assertThat(get(base() + "/" + id + "/events")).hasSize(1);
    }

    @Test
    void newInboundReopensClosedConversationAndDuplicateDoesNotReopenAgain() {
        JsonNode first = inbound(fixture, "first-" + UUID.randomUUID());
        String id = first.path("conversationId").asText();
        long version = get(base() + "/" + id)
            .path("version")
            .asLong();
        JsonNode assigned = action(id, "assume", fixture.human(), null, version, 200);
        action(id, "close", fixture.human(), null, assigned.path("version").asLong(), 200);
        String external = "reopen-" + UUID.randomUUID();
        JsonNode next = inbound(fixture, external);
        assertThat(inbound(fixture, external).path("id")).isEqualTo(next.path("id"));
        JsonNode current = get(base() + "/" + id);
        assertThat(current.path("status").asText()).isEqualTo("PENDING");
        assertThat(current.path("assignedMemberId").isNull()).isTrue();
        assertThat(current.path("closedAt").isNull()).isTrue();
        List<String> events = StreamSupport.stream(get(base() + "/" + id + "/events").spliterator(), false)
            .map(row -> row.path("event").asText())
            .toList();
        assertThat(Collections.frequency(events, "CONVERSATION_AUTO_REOPENED")).isEqualTo(1);
        assertThat(get(base() + "/" + id + "/messages")).hasSize(2);
    }

    @Test
    void unreadBoundaryUsesTimestampAndIdIsMonotonicAndExcludesOutbound() {
        String id = conversation("Leitura", "PENDING", null, TIME);
        String older = message(fixture, id, "inbound", TIME.minusSeconds(1));
        String first = message(fixture, id, "INBOUND", TIME);
        String second = message(fixture, id, "INBOUND", TIME);
        message(fixture, id, "OUTBOUND", TIME.plusSeconds(1));
        assertThat(
            queue("readerMemberId=" + fixture.human())
                .get(0)
                .path("unreadCount")
                .asLong()
        ).isEqualTo(3);
        assertThat(queue("").get(0).path("unreadCount").isNull()).isTrue();
        assertThat(read(id, fixture.human(), first, 200).path("unreadCount").asLong()).isEqualTo(1);
        assertThat(read(id, fixture.human(), older, 200).path("throughMessageId").asText()).isEqualTo(first);
        read(id, fixture.bot(), second, 403);
        read(id, Long.MAX_VALUE, second, 403);
        String unrelated = conversation("Outro contato", "PENDING", null, TIME);
        String unrelatedMessage = message(fixture, unrelated, "INBOUND", TIME);
        read(id, fixture.human(), unrelatedMessage, 404);
        read(id, fixture.human(), "invalid", 400);
        var a = CompletableFuture.supplyAsync(() -> read(id, fixture.human(), first, 200));
        var b = CompletableFuture.supplyAsync(() -> read(id, fixture.human(), second, 200));
        a.join();
        b.join();
        assertThat(read(id, fixture.human(), second, 200).path("unreadCount").asLong()).isZero();
        assertThat(
            mongo
                .getCollection("conversation_reads")
                .countDocuments(
                    new Document("conversationId", new ObjectId(id)).append("memberId", fixture.human())
                )
        ).isEqualTo(1);
        message(fixture, id, "INBOUND", TIME.plusSeconds(3));
        JsonNode row = StreamSupport.stream(queue("readerMemberId=" + fixture.human()).spliterator(), false)
            .filter(item -> item.path("id").asText().equals(id))
            .findFirst()
            .orElseThrow();
        assertThat(row.path("unreadCount").asLong()).isEqualTo(1);
    }

    @Test
    void messageCursorWalksEveryMessageWithTimestampTiesWithoutDuplicates() {
        String id = conversation("Histórico", "PENDING", null, TIME);
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            expected.add(message(fixture, id, "INBOUND", TIME));
        }
        String path = base() + "/" + id + "/messages";
        JsonNode page = get(path + "?limit=30");
        List<String> received = new ArrayList<>(ids(page.path("items")));
        assertThat(page.path("items")).hasSize(30);
        while (page.path("hasMore").asBoolean()) {
            page = get(path + "?limit=30&before=" + encode(page.path("nextCursor").asText()));
            received.addAll(ids(page.path("items")));
        }
        assertThat(received)
            .hasSize(65)
            .doesNotHaveDuplicates()
            .containsExactlyInAnyOrderElementsOf(expected);
        assertThat(page.path("items")).hasSize(5);
        assertThat(page.path("nextCursor").isNull()).isTrue();
        call(path + "?limit=0", HttpMethod.GET, null, 400);
        call(path + "?limit=101", HttpMethod.GET, null, 400);
        call(path + "?limit=30&before=invalid", HttpMethod.GET, null, 400);
    }

    @Test
    void transfersSupportHumanHumanHumanBotBotBotAndBotHumanWithAuditedMembers() {
        String prefix = "/api";
        long human = call(
            prefix + "/users",
            HttpMethod.POST,
            LocalFlowIntegrationTest.account(
                "Bruno",
                com.wxn.chatbot.user.entity.UserRole.ATTENDANT,
                List.of(fixture.department())
            ),
            201
        )
            .path("memberId")
            .asLong();
        long bot = call(prefix + "/members", HttpMethod.POST, Map.of("name", "Bot 2", "type", "BOT"), 201)
            .path("id")
            .asLong();
        String id = conversation("Transferências", "PENDING", null, TIME);
        action(id, "assign", fixture.human(), fixture.human(), 0, 200);
        long previous = fixture.human();
        long version = 1;
        for (long next : List.of(human, fixture.bot(), bot, fixture.human())) {
            JsonNode result = action(id, "transfer", fixture.human(), next, version++, 200);
            assertThat(result.path("assignedMemberId").asLong()).isEqualTo(next);
            assertThat(result.path("status").asText()).isEqualTo("IN_PROGRESS");
            JsonNode events = get(base() + "/" + id + "/events");
            JsonNode event = events.get(events.size() - 1);
            assertThat(event.path("previousMemberId").asLong()).isEqualTo(previous);
            assertThat(event.path("newMemberId").asLong()).isEqualTo(next);
            previous = next;
        }
        assertThat(get(base() + "/" + id + "/events")).hasSize(5);
        action(id, "transfer", fixture.human(), fixture.human(), version, 200);
        assertThat(get(base() + "/" + id + "/events")).hasSize(5);
        jdbc.update("UPDATE members SET status='INACTIVE' WHERE id=?", bot);
        action(id, "transfer", fixture.human(), bot, version, 409);
    }

    @Test
    void readStateIsIndependentPerHumanAndDoesNotChangeMessageDeliveryStatus() {
        var other = LocalFlowIntegrationTest.account(
            "Outro administrador",
            com.wxn.chatbot.user.entity.UserRole.ADMIN,
            List.of()
        );
        long human = accountUsers.create(other).memberId();
        String id = conversation("Leitura individual", "PENDING", null, TIME);
        String msg = message(fixture, id, "INBOUND", TIME);
        read(id, fixture.human(), msg, 200);
        assertThat(
            queue("readerMemberId=" + fixture.human())
                .get(0)
                .path("unreadCount")
                .asLong()
        ).isZero();
        authenticate(other);
        assertThat(
            queue("readerMemberId=" + human)
                .get(0)
                .path("unreadCount")
                .asLong()
        ).isEqualTo(1);
        assertThat(
            get(base() + "/" + id + "/messages")
                .get(0)
                .path("status")
                .asText()
        ).isEqualTo("RECEIVED");
    }
}
