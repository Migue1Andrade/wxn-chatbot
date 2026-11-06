package com.wxn.chatbot.notification.controller;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;
    private final AccountSessions sessions;

    public NotificationController(NotificationService service, AccountSessions sessions) {
        this.service = service;
        this.sessions = sessions;
    }

    private Long user(HttpServletRequest r) {
        return sessions.require(r.getSession(false)).id();
    }

    @GetMapping
    public java.util.List<NotificationService.Response> list(
        HttpServletRequest r,
        @RequestParam(defaultValue = "0") int page
    ) {
        return service.list(user(r), page);
    }

    @GetMapping("/unread-count")
    public java.util.Map<String, Long> count(HttpServletRequest r) {
        return java.util.Map.of("count", service.count(user(r)));
    }

    @PatchMapping("/{id}/read")
    public void read(@PathVariable Long id, HttpServletRequest r) {
        service.read(user(r), id);
    }

    @PostMapping("/read-all")
    public void readAll(HttpServletRequest r) {
        service.readAll(user(r));
    }
}
