package com.wxn.chatbot.auth.controller;

import com.wxn.chatbot.auth.service.AccountSessions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AccountSessionController {

    private final AccountSessions sessions;

    public AccountSessionController(AccountSessions sessions) {
        this.sessions = sessions;
    }

    public record Login(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 72) String password
    ) {}

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
}
