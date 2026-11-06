package com.wxn.chatbot.kanban.controller;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.kanban.dto.KanbanDtos.*;
import com.wxn.chatbot.kanban.service.KanbanViewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/kanban/views")
public class KanbanViewController {

    private final KanbanViewService service;
    private final AccountSessions sessions;

    public KanbanViewController(KanbanViewService service, AccountSessions sessions) {
        this.service = service;
        this.sessions = sessions;
    }

    private Long owner(HttpServletRequest r) {
        return sessions.require(r.getSession(false)).id();
    }

    @GetMapping
    public List<View> list(HttpServletRequest r) {
        return service.list(owner(r));
    }

    @PostMapping
    public View create(HttpServletRequest r, @Valid @RequestBody ViewInput input) {
        return service.save(null, owner(r), input);
    }

    @PatchMapping("/{id}")
    public View update(@PathVariable Long id, HttpServletRequest r, @Valid @RequestBody ViewInput input) {
        return service.save(id, owner(r), input);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, HttpServletRequest r) {
        service.delete(id, owner(r));
    }
}
