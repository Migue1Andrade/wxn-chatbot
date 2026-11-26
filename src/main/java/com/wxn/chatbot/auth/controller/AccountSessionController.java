package com.wxn.chatbot.auth.controller;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.auth.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AccountSessionController {

    private final AccountSessions sessions;
    private final PasswordResetService passwordResets;

    public AccountSessionController(AccountSessions sessions, PasswordResetService passwordResets) {
        this.sessions = sessions;
        this.passwordResets = passwordResets;
    }

    public record Login(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 72) String password
    ) {}

    public record PasswordResetRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record PasswordResetConfirmation(
        @NotBlank @Size(max = 128) String token,
        @NotBlank @Size(min = 8, max = 256) String password
    ) {
        @AssertTrue(message = "A senha deve ter no máximo 72 bytes em UTF-8")
        public boolean isPasswordWithinEncoderLimit() {
            return (
                password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72
            );
        }
    }

    @GetMapping("/session")
    public AccountSessions.State state(HttpServletRequest request) {
        return sessions.state(request);
    }

    @PostMapping("/login")
    public AccountSessions.State login(HttpServletRequest request, @Valid @RequestBody Login login) {
        return sessions.login(request, login.username(), login.password());
    }

    @PostMapping("/logout")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public void requestPasswordReset(
        HttpServletRequest request,
        @Valid @RequestBody PasswordResetRequest body
    ) {
        passwordResets.request(body.email(), request.getRemoteAddr());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(
        HttpServletRequest request,
        @Valid @RequestBody PasswordResetConfirmation body
    ) {
        passwordResets.confirm(body.token(), body.password(), request.getRemoteAddr());
    }
}
