package com.wxn.chatbot.demo.controller;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.config.DevelopmentAccess;
import com.wxn.chatbot.demo.service.LocalDemoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("dev & !prod")
@RequestMapping("/api/dev/demo")
public class LocalDemoController {

    public record Input(@NotBlank @Size(min = 8, max = 72) String password) {}

    private final LocalDemoService service;
    private final AccountSessions sessions;
    private final DevelopmentAccess access;

    public LocalDemoController(LocalDemoService service, AccountSessions sessions, DevelopmentAccess access) {
        this.service = service;
        this.sessions = sessions;
        this.access = access;
    }

    @PostMapping
    public LocalDemoService.Report seed(@Valid @RequestBody Input input, HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (
            !access.enabled() ||
            request.getHeader("Origin") != null ||
            !java.util.Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(remote)
        ) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "Seed permitido somente por execução manual local em dev"
            );
        }
        return service.run(input.password(), sessions.require(request.getSession(false)));
    }
}
