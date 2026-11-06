package com.wxn.chatbot.message.controller;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.message.dto.MessageDtos;
import com.wxn.chatbot.message.dto.MessagePage;
import com.wxn.chatbot.message.service.MessageService;
import com.wxn.chatbot.whatsapp.model.NormalizedMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {

    private static final int DEFAULT_CURSOR_LIMIT = 30;
    private final AccountSessions sessions;
    private final HttpServletRequest httpRequest;
    private final MessageService service;
    private final com.wxn.chatbot.message.service.MessageActionsService actions;

    public MessageController(
        MessageService service,
        AccountSessions sessions,
        HttpServletRequest httpRequest,
        com.wxn.chatbot.message.service.MessageActionsService actions
    ) {
        this.actions = actions;
        this.sessions = sessions;
        this.httpRequest = httpRequest;
        this.service = service;
    }

    @GetMapping(params = { "!limit", "!before" })
    public List<NormalizedMessage> history(
        @PathVariable String conversationId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "100") int size
    ) {
        return service.history(conversationId, page, size);
    }

    @GetMapping(params = "limit")
    public MessagePage page(
        @PathVariable String conversationId,
        @RequestParam int limit,
        @RequestParam(required = false) String before
    ) {
        return service.page(conversationId, limit, before);
    }

    @GetMapping(params = { "before", "!limit" })
    public MessagePage previous(@PathVariable String conversationId, @RequestParam String before) {
        return service.page(conversationId, DEFAULT_CURSOR_LIMIT, before);
    }

    @PostMapping("/{messageId}/reply")
    public NormalizedMessage reply(
        @PathVariable String conversationId,
        @PathVariable String messageId,
        @Valid @RequestBody MessageDtos.Text request
    ) {
        return actions.reply(
            conversationId,
            messageId,
            request,
            sessions.require(httpRequest.getSession(false))
        );
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public NormalizedMessage send(
        @PathVariable String conversationId,
        @Valid @RequestBody MessageDtos.Text request
    ) {
        return actions.send(conversationId, request, sessions.require(httpRequest.getSession(false)));
    }
}
