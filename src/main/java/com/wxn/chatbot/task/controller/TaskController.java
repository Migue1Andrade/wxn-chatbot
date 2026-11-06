package com.wxn.chatbot.task.controller;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.task.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService service;
    private final AccountSessions sessions;

    public TaskController(TaskService service, AccountSessions sessions) {
        this.service = service;
        this.sessions = sessions;
    }

    @GetMapping
    public java.util.List<TaskService.Response> list(
        HttpServletRequest r,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(required = false) String status
    ) {
        return service.list(sessions.require(r.getSession(false)), page, status);
    }

    @GetMapping("/{id}")
    public TaskService.Response get(@PathVariable Long id, HttpServletRequest r) {
        return service.get(id, sessions.require(r.getSession(false)));
    }

    @PostMapping
    public TaskService.Response create(HttpServletRequest r, @Valid @RequestBody TaskService.Input input) {
        return service.save(null, input, sessions.require(r.getSession(false)));
    }

    @PatchMapping("/{id}")
    public TaskService.Response update(
        @PathVariable Long id,
        HttpServletRequest r,
        @Valid @RequestBody TaskService.Input input
    ) {
        return service.save(id, input, sessions.require(r.getSession(false)));
    }
}
