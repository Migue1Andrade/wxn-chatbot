package com.wxn.chatbot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.wxn.chatbot.common.ActiveStatus;
import com.wxn.chatbot.user.dto.UserDtos;
import com.wxn.chatbot.user.entity.UserRole;
import com.wxn.chatbot.user.service.UserService;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

/** Explicit opt-in; requires isolated PostgreSQL, MongoDB and Mailpit services. */
@EnabledIfEnvironmentVariable(named = "WXN_INTEGRATION_TEST", matches = "true")
@ActiveProfiles({ "dev", "legacy" })
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "wxn.dev.api-key=integration-test-key-minimum-32-characters",
        "spring.mail.host=localhost",
        "spring.mail.port=${SMTP_PORT:14025}",
        "wxn.auth.password-reset.public-url=http://localhost:4002/#!/reset-password",
        "openbot.support.enabled=false",
    }
)
class PasswordResetIntegrationTest {

    private static final String KEY = "integration-test-key-minimum-32-characters";
    private static final Pattern TOKEN = Pattern.compile("/#!/reset-password\\?token=([A-Za-z0-9_-]{40,})");

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserService users;

    @Value("${test.mailpit-url:http://localhost:18025}")
    String mailpitUrl;

    @org.junit.jupiter.api.BeforeEach
    void resetReviewRateLimitFixtures() {
        jdbc.update("delete from password_reset_rate_limits");
    }

    @Test
    void resetSendsLocalMailConsumesExpiresAndRevokesPreviousSession() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = suffix + "@example.invalid";
        String username = "reset" + suffix;
        var created = users.create(
            new UserDtos.Create(
                "Conta de recuperação",
                username,
                email,
                validCpf(),
                null,
                "old-fixture-password",
                "old-fixture-password",
                UserRole.ADMIN,
                ActiveStatus.ACTIVE,
                List.of()
            )
        );
        var originalSession = login(username, "old-fixture-password");

        String unknownEmail = "unknown@example.invalid";
        var unknown = post("/api/auth/password-reset/request", java.util.Map.of("email", unknownEmail));
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(mailpitMessageCount(unknownEmail)).isZero();
        var requested = post("/api/auth/password-reset/request", java.util.Map.of("email", email));
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String firstToken = readLatestTokenFromMail(email);
        assertThat(
            jdbc.queryForObject(
                "select token_hash from password_reset_tokens where user_id = ?",
                String.class,
                created.id()
            )
        )
            .hasSize(64)
            .isNotEqualTo(firstToken);
        assertThat(
            post(
                "/api/auth/password-reset/confirm",
                java.util.Map.of("token", "invalid-fixture-token", "password", "new-fixture-password")
            ).getStatusCode()
        ).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
            jdbc.queryForObject(
                "select request_count from password_reset_rate_limits where rate_key = ?",
                Integer.class,
                hash("confirm-ip:127.0.0.1")
            )
        ).isEqualTo(1);

        var gate = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var confirmation = java.util.Map.of("token", firstToken, "password", "new-fixture-password");
        var firstAttempt = executor.submit(() -> confirmWhenReleased(gate, confirmation));
        var secondAttempt = executor.submit(() -> confirmWhenReleased(gate, confirmation));
        gate.countDown();
        var statuses = List.of(firstAttempt.get().getStatusCode(), secondAttempt.get().getStatusCode());
        executor.shutdownNow();
        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.BAD_REQUEST);
        assertThat(
            jdbc.queryForObject("select credential_version from users where id = ?", Long.class, created.id())
        ).isEqualTo(1L);
        JsonNode invalidated = getWithCookie("/api/auth/session", originalSession.cookie()).getBody();
        assertThat(invalidated.path("user").isNull())
            .withFailMessage("old session state: " + invalidated)
            .isTrue();
        assertThat(
            post(
                "/api/auth/password-reset/confirm",
                java.util.Map.of("token", firstToken, "password", "other-fixture-password")
            ).getStatusCode()
        ).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(login(username, "new-fixture-password").response().getStatusCode()).isEqualTo(
            HttpStatus.OK
        );

        post("/api/auth/password-reset/request", java.util.Map.of("email", email));
        String expiringToken = readLatestTokenFromMail(email);
        jdbc.update(
            "update password_reset_tokens set expires_at = now() - interval '1 second' " +
                "where user_id = ? and consumed_at is null",
            created.id()
        );
        assertThat(
            post(
                "/api/auth/password-reset/confirm",
                java.util.Map.of("token", expiringToken, "password", "expired-fixture-password")
            ).getStatusCode()
        ).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<JsonNode> post(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Dev-Api-Key", KEY);
        headers.setOrigin("http://localhost:4002");
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> confirmWhenReleased(
        java.util.concurrent.CountDownLatch gate,
        Object body
    ) {
        try {
            gate.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrompido antes da confirmação", interrupted);
        }
        return post("/api/auth/password-reset/confirm", body);
    }

    private ResponseEntity<JsonNode> getWithCookie(String path, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Dev-Api-Key", KEY);
        headers.set(HttpHeaders.COOKIE, cookie);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    private LoginResult login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Dev-Api-Key", KEY);
        var state = http.exchange(
            "/api/auth/session",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            JsonNode.class
        );
        String cookie = state.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
        headers.set(HttpHeaders.COOKIE, cookie);
        headers.setOrigin("http://localhost:4002");
        headers.set("X-CSRF-Token", state.getBody().path("csrfToken").asText());
        var loggedIn = http.exchange(
            "/api/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(java.util.Map.of("username", username, "password", password), headers),
            JsonNode.class
        );
        return new LoginResult(
            loggedIn,
            loggedIn.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0]
        );
    }

    private String readLatestTokenFromMail(String email) {
        RestTemplate mailbox = new RestTemplate();
        var deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode index = mailbox.getForObject(mailpitUrl + "/api/v1/messages", JsonNode.class);
            String latestToken = null;
            java.time.Instant latestDate = java.time.Instant.MIN;
            for (JsonNode entry : index.path("messages")) {
                String id = entry.path("ID").asText();
                JsonNode message = mailbox.getForObject(mailpitUrl + "/api/v1/message/" + id, JsonNode.class);
                String text = message == null ? "" : message.path("Text").asText();
                if (message != null && message.path("To").toString().contains(email)) {
                    String created = entry.path("Created").asText();
                    if (created.isBlank()) {
                        created = message.path("Date").asText();
                    }
                    var matcher = TOKEN.matcher(text);
                    if (matcher.find() && !created.isBlank()) {
                        var date = java.time.OffsetDateTime.parse(created).toInstant();
                        if (date.isAfter(latestDate)) {
                            latestDate = date;
                            latestToken = matcher.group(1);
                        }
                    }
                }
            }
            if (latestToken != null) {
                return latestToken;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrompido enquanto aguardava o e-mail local", interrupted);
            }
        }
        throw new AssertionError("A mensagem de recuperação não apareceu na caixa Mailpit local");
    }

    private int mailpitMessageCount(String email) {
        JsonNode index = new RestTemplate().getForObject(mailpitUrl + "/api/v1/messages", JsonNode.class);
        int count = 0;
        for (JsonNode entry : index.path("messages")) {
            String id = entry.path("ID").asText();
            JsonNode message = new RestTemplate().getForObject(
                mailpitUrl + "/api/v1/message/" + id,
                JsonNode.class
            );
            if (message != null && message.path("To").toString().contains(email)) {
                count++;
            }
        }
        return count;
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                )
            );
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private String validCpf() {
        String digits = String.format("%09d", new java.util.Random().nextInt(999_999_999));
        for (int size = 9; size <= 10; size++) {
            int sum = 0;
            for (int index = 0; index < size; index++) {
                sum += (digits.charAt(index) - '0') * (size + 1 - index);
            }
            int remainder = sum % 11;
            digits += remainder < 2 ? "0" : Integer.toString(11 - remainder);
        }
        return digits;
    }

    private record LoginResult(ResponseEntity<JsonNode> response, String cookie) {}
}
