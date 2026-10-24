package com.wxn.chatbot.message.controller;

import com.wxn.chatbot.message.dto.MessageDtos;
import com.wxn.chatbot.message.dto.MessagePage;
import com.wxn.chatbot.message.service.MessageService;
import com.wxn.chatbot.whatsapp.model.NormalizedMessage;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/conversations/{conversationId}/messages")
public class MessageController {

    private static final int DEFAULT_CURSOR_LIMIT = 30;
    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @GetMapping(params = { "!limit", "!before" })
    public List<NormalizedMessage> history(
        @PathVariable Long companyId,
        @PathVariable String conversationId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "100") int size
    ) {
        return service.history(companyId, conversationId, page, size);
    }

    @GetMapping(params = "limit")
    public MessagePage page(
        @PathVariable Long companyId,
        @PathVariable String conversationId,
        @RequestParam int limit,
        @RequestParam(required = false) String before
    ) {
        return service.page(companyId, conversationId, limit, before);
    }

    @GetMapping(params = { "before", "!limit" })
    public MessagePage previous(
        @PathVariable Long companyId,
        @PathVariable String conversationId,
        @RequestParam String before
    ) {
        return service.page(companyId, conversationId, DEFAULT_CURSOR_LIMIT, before);
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public NormalizedMessage send(
        @PathVariable Long companyId,
        @PathVariable String conversationId,
        @Valid @RequestBody MessageDtos.Send request
    ) {
        return service.send(companyId, conversationId, request);
    }
}
