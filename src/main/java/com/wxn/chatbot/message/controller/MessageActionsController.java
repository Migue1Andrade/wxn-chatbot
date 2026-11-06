package com.wxn.chatbot.message.controller;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.message.dto.MessageDtos;
import com.wxn.chatbot.message.service.MessageActionsService;
import com.wxn.chatbot.whatsapp.model.*;
import com.wxn.chatbot.whatsapp.model.DeleteMessageRequest.DeleteIntent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api")
public class MessageActionsController {

    private final MessageActionsService service;
    private final AccountSessions sessions;
    private final HttpServletRequest request;

    public MessageActionsController(
        MessageActionsService service,
        AccountSessions sessions,
        HttpServletRequest request
    ) {
        this.service = service;
        this.sessions = sessions;
        this.request = request;
    }

    @GetMapping("/conversations/{id}/message-capabilities")
    public MessageActionsService.Capabilities capabilities(@PathVariable String id) {
        return service.capabilities(id, sessions.require(request.getSession(false)));
    }

    @PostMapping("/messages/{id}/forward")
    public NormalizedMessage forward(@PathVariable String id, @Valid @RequestBody MessageDtos.Forward body) {
        return service.forward(id, body, sessions.require(request.getSession(false)));
    }

    @DeleteMapping("/messages/{id}")
    public NormalizedMessage delete(
        @PathVariable String id,
        @RequestParam DeleteIntent intent,
        @RequestParam @Pattern(regexp = "[a-zA-Z0-9_-]{16,100}") String operationId
    ) {
        return service.delete(id, intent, operationId, sessions.require(request.getSession(false)));
    }
}
