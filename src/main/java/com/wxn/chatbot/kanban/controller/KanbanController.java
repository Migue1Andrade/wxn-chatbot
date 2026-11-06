package com.wxn.chatbot.kanban.controller;

import com.wxn.chatbot.kanban.dto.KanbanDtos.*;
import com.wxn.chatbot.kanban.service.KanbanService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/kanban")
public class KanbanController {

    private final KanbanService service;
    private final com.wxn.chatbot.auth.service.AccountSessions sessions;

    public KanbanController(KanbanService service, com.wxn.chatbot.auth.service.AccountSessions sessions) {
        this.sessions = sessions;
        this.service = service;
    }

    @GetMapping({ "", "/conversations" })
    public Page page(@Valid @ModelAttribute Filter filter) {
        return service.page(filter);
    }

    @GetMapping("/metrics")
    public Metrics metrics() {
        return service.metrics();
    }

    @GetMapping("/groups")
    public java.util.List<Group> groups() {
        return service.metrics().groups();
    }

    @GetMapping("/conversations/{id}")
    public Card details(@PathVariable String id) {
        return service.details(id);
    }

    @PatchMapping("/conversations/{id}/priority")
    public Card priority(
        @PathVariable String id,
        @Valid @RequestBody Priority body,
        jakarta.servlet.http.HttpServletRequest request
    ) {
        return service.priority(id, body, sessions.require(request.getSession(false)).memberId());
    }
}
