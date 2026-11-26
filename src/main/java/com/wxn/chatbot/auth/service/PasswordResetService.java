package com.wxn.chatbot.auth.service;

import com.wxn.chatbot.common.ActiveStatus;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.user.entity.User;
import com.wxn.chatbot.user.repository.UserRepository;
import com.wxn.chatbot.user.service.UserService;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PasswordResetService {

    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(20);
    private static final Duration RATE_WINDOW = Duration.ofHours(1);
    private static final int EMAIL_REQUEST_LIMIT = 3;
    private static final int IP_REQUEST_LIMIT = 8;
    private static final int IP_ATTEMPT_LIMIT = 12;
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final UserService userService;
    private final JavaMailSender mail;
    private final SecureRandom random = new SecureRandom();
    private final String publicUrl;
    private final String from;
    private final TransactionTemplate independentTransaction;
    private final ThreadPoolTaskExecutor mailExecutor;

    public PasswordResetService(
        JdbcTemplate jdbc,
        UserRepository users,
        UserService userService,
        JavaMailSender mail,
        Environment environment,
        @Value("${wxn.auth.password-reset.public-url}") String publicUrl,
        @Value("${wxn.auth.password-reset.from}") String from,
        PlatformTransactionManager transactionManager,
        ThreadPoolTaskExecutor mailExecutor
    ) {
        this.jdbc = jdbc;
        this.users = users;
        this.userService = userService;
        this.mail = mail;
        this.publicUrl = validatePublicUrl(publicUrl, environment.acceptsProfiles(Profiles.of("prod")));
        this.from = from;
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        this.mailExecutor = mailExecutor;
    }

    @Transactional
    public void request(String email, String remoteAddress) {
        cleanExpiredRecords();
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        boolean allowed = takeLimit("request-ip", remoteAddress, IP_REQUEST_LIMIT);
        allowed &= takeLimit("request-email", normalized, EMAIL_REQUEST_LIMIT);
        if (!allowed) {
            return;
        }

        User account = users.findByEmailIgnoreCase(normalized).orElse(null);
        String link = null;
        if (
            allowed &&
            account != null &&
            account.getDeletedAt() == null &&
            account.getAccountStatus() == ActiveStatus.ACTIVE &&
            !account.isRegistrationIncomplete()
        ) {
            account = users.lockById(account.getId()).orElse(null);
            if (account != null && account.getAccountStatus() == ActiveStatus.ACTIVE) {
                jdbc.update(
                    "update password_reset_tokens set consumed_at = now() where user_id = ? and consumed_at is null",
                    account.getId()
                );
                String token = newToken();
                jdbc.update(
                    "insert into password_reset_tokens(user_id, token_hash, expires_at) values (?, ?, now() + (? * interval '1 second'))",
                    account.getId(),
                    hash(token),
                    TOKEN_LIFETIME.toSeconds()
                );
                link = publicUrl + "?token=" + token;
            }
        }
        if (link != null) {
            String messageLink = link;
            Runnable send = () -> {
                try {
                    sendMessage(normalized, messageLink);
                } catch (RuntimeException failure) {
                    // Do not log account identifiers, tokens, or mail contents.
                }
            };
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            mailExecutor.execute(send);
                        }
                    }
                );
            } else {
                mailExecutor.execute(send);
            }
        }
    }

    @Transactional
    public void confirm(String token, String password, String remoteAddress) {
        boolean withinLimit = independentTransaction.execute(status ->
            takeLimit("confirm-ip", remoteAddress, IP_ATTEMPT_LIMIT)
        );
        if (!Boolean.TRUE.equals(withinLimit)) {
            throw invalidToken();
        }
        cleanExpiredRecords();
        String tokenHash = hash(token);
        Long userId = jdbc.query(
            "select user_id from password_reset_tokens where token_hash = ? and consumed_at is null and expires_at > now()",
            result -> result.next() ? result.getLong(1) : null,
            tokenHash
        );
        if (userId == null) {
            throw invalidToken();
        }

        User account = users.lockById(userId).orElseThrow(PasswordResetService::invalidToken);
        int consumed = jdbc.update(
            "update password_reset_tokens set consumed_at = now() where token_hash = ? and user_id = ? and consumed_at is null and expires_at > now()",
            tokenHash,
            userId
        );
        if (consumed != 1) {
            throw invalidToken();
        }
        userService.resetPassword(account.getId(), password);
        jdbc.update(
            "update password_reset_tokens set consumed_at = now() where user_id = ? and consumed_at is null",
            userId
        );
    }

    private boolean takeLimit(String category, String value, int limit) {
        String key = hash(category + ":" + (value == null ? "unknown" : value));
        Integer count = jdbc.queryForObject(
            "insert into password_reset_rate_limits(rate_key, window_started_at, request_count) values (?, now(), 1) " +
                "on conflict(rate_key) do update set " +
                "request_count = case when password_reset_rate_limits.window_started_at <= now() - (? * interval '1 second') then 1 else password_reset_rate_limits.request_count + 1 end, " +
                "window_started_at = case when password_reset_rate_limits.window_started_at <= now() - (? * interval '1 second') then now() else password_reset_rate_limits.window_started_at end " +
                "returning request_count",
            Integer.class,
            key,
            RATE_WINDOW.toSeconds(),
            RATE_WINDOW.toSeconds()
        );
        return count != null && count <= limit;
    }

    private void cleanExpiredRecords() {
        jdbc.update(
            "delete from password_reset_tokens where expires_at < now() - interval '7 days' " +
                "or consumed_at < now() - interval '7 days'"
        );
        jdbc.update(
            "delete from password_reset_rate_limits where window_started_at < now() - interval '24 hours'"
        );
    }

    private void sendMessage(String email, String link) {
        MimeMessage message = mail.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("Redefinição de senha WXN Chatbot");
            String content =
                "Se houver uma conta cadastrada para este e-mail, use o link para redefinir sua senha.\n";
            if (link != null) {
                content += "\nEste link expira em 20 minutos e pode ser usado uma única vez:\n" + link + "\n";
            }
            content += "\nSe você não solicitou a redefinição, ignore esta mensagem.";
            helper.setText(content, false);
            mail.send(message);
        } catch (MailException | jakarta.mail.MessagingException failure) {
            throw new IllegalStateException("Password reset email delivery failed");
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String validatePublicUrl(String value, boolean production) {
        try {
            var uri = java.net.URI.create(value);
            boolean localHttp =
                "http".equalsIgnoreCase(uri.getScheme()) &&
                ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            if (
                !uri.isAbsolute() ||
                !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) ||
                ("http".equalsIgnoreCase(uri.getScheme()) && (!localHttp || production)) ||
                uri.getHost() == null ||
                uri.getUserInfo() != null ||
                uri.getRawQuery() != null ||
                uri.getFragment() == null ||
                !uri.getFragment().matches("!/reset-password")
            ) {
                throw new IllegalArgumentException("URL pública de redefinição inválida");
            }
            return value;
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException(
                "Configure uma URL pública HTTP(S) confiável para redefinição",
                invalid
            );
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponível", impossible);
        }
    }

    private static ApiException invalidToken() {
        return new ApiException(
            org.springframework.http.HttpStatus.BAD_REQUEST,
            "Link inválido, expirado ou já utilizado"
        );
    }
}
