package com.wxn.chatbot.message.controller;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.message.service.MessageMutationService;
import com.wxn.chatbot.whatsapp.model.NormalizedMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages/{id}/operations")
public class MessageMutationController {

    public record Input(
        @NotBlank @Pattern(regexp = "EDIT_TEXT|SEND_REACTION|REMOVE_REACTION|MARK_READ") String operation,
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{16,100}") String operationId,
        @Size(max = 1600) String value
    ) {}

    private final MessageMutationService service;
    private final AccountSessions sessions;

    public MessageMutationController(MessageMutationService service, AccountSessions sessions) {
        this.service = service;
        this.sessions = sessions;
    }

    @PostMapping
    public NormalizedMessage apply(
        @PathVariable String id,
        @Valid @RequestBody Input input,
        HttpServletRequest request
    ) {
        return service.mutate(
            id,
            input.operation(),
            input.operationId(),
            input.value(),
            sessions.require(request.getSession(false))
        );
    }
}
