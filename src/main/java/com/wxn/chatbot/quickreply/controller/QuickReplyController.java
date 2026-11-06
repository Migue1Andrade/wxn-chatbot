package com.wxn.chatbot.quickreply.controller;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.quickreply.dto.QuickReplyDtos.*;
import com.wxn.chatbot.quickreply.service.QuickReplyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quick-replies")
public class QuickReplyController {

    private final QuickReplyService service;
    private final AccountSessions sessions;

    public QuickReplyController(QuickReplyService service, AccountSessions sessions) {
        this.service = service;
        this.sessions = sessions;
    }

    @GetMapping
    public java.util.List<Response> list(
        HttpServletRequest r,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(required = false) String scope
    ) {
        return service.list(sessions.require(r.getSession(false)), page, scope);
    }

    @PostMapping
    public Response create(HttpServletRequest r, @Valid @RequestBody Input input) {
        return service.save(null, input, sessions.require(r.getSession(false)));
    }

    @PatchMapping("/{id}")
    public Response update(@PathVariable Long id, HttpServletRequest r, @Valid @RequestBody Input input) {
        return service.save(id, input, sessions.require(r.getSession(false)));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, HttpServletRequest r) {
        service.delete(id, sessions.require(r.getSession(false)));
    }

    @PostMapping("/preview")
    public Preview preview(HttpServletRequest r, @Valid @RequestBody PreviewInput input) {
        return service.preview(input, sessions.require(r.getSession(false)));
    }
}
