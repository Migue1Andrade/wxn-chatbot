package com.wxn.chatbot.collaboration.controller;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.collaboration.dto.TeamDtos.*;
import com.wxn.chatbot.collaboration.service.TeamService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TeamController {

    private final TeamService service;
    private final AccountSessions sessions;

    public TeamController(TeamService service, AccountSessions sessions) {
        this.service = service;
        this.sessions = sessions;
    }

    private AccountSessions.Identity actor(HttpServletRequest r) {
        return sessions.require(r.getSession(false));
    }

    @GetMapping("/conversations/{id}/notes")
    public List<Entry> notes(
        @PathVariable String id,
        @RequestParam(defaultValue = "0") int page,
        HttpServletRequest r
    ) {
        return service.list(id, "NOTE", page, actor(r));
    }

    @PostMapping("/conversations/{id}/notes")
    public Entry note(@PathVariable String id, @Valid @RequestBody Create input, HttpServletRequest r) {
        return service.create(id, "NOTE", input, actor(r));
    }

    @GetMapping("/conversations/{id}/internal-messages")
    public List<Entry> internal(
        @PathVariable String id,
        @RequestParam(defaultValue = "0") int page,
        HttpServletRequest r
    ) {
        return service.list(id, "INTERNAL", page, actor(r));
    }

    @PostMapping("/conversations/{id}/internal-messages")
    public Entry internal(@PathVariable String id, @Valid @RequestBody Create input, HttpServletRequest r) {
        return service.create(id, "INTERNAL", input, actor(r));
    }

    @PatchMapping({ "/notes/{id}", "/internal-messages/{id}" })
    public Entry edit(@PathVariable String id, @Valid @RequestBody Edit input, HttpServletRequest r) {
        return service.edit(id, input, false, actor(r));
    }

    @DeleteMapping({ "/notes/{id}", "/internal-messages/{id}" })
    public Entry delete(@PathVariable String id, @RequestParam long version, HttpServletRequest r) {
        return service.edit(id, new Edit("Removido", false, version), true, actor(r));
    }

    @GetMapping({ "/notes/{id}/history", "/internal-messages/{id}/history" })
    public List<Revision> history(@PathVariable String id, HttpServletRequest r) {
        return service.history(id, actor(r));
    }

    @GetMapping("/conversations/{id}/draft")
    public java.util.Map<String, String> draft(
        @PathVariable String id,
        @RequestParam String mode,
        HttpServletRequest r
    ) {
        return java.util.Map.of("content", service.draft(id, mode, actor(r)));
    }

    @PutMapping("/conversations/{id}/draft")
    public void draft(@PathVariable String id, @Valid @RequestBody Draft input, HttpServletRequest r) {
        service.draft(id, input, actor(r));
    }
}
