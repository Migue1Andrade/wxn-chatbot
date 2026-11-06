package com.wxn.chatbot.whatsapp.operations;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.whatsapp.model.ConnectionInstructions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/channels/{id}")
public class ChannelOperationsController {

    public record Operation(
        @NotBlank @Pattern(regexp = "restart|disconnect") String operation,
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{16,100}") String operationId,
        @NotBlank String confirmation
    ) {}

    public record Webhooks(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{16,100}") String operationId,
        @NotBlank String confirmation
    ) {}

    public record Policy(boolean enabled) {}

    public record Reconcile(
        @NotBlank String messageId,
        @NotBlank @Size(max = 255) String externalMessageId
    ) {}

    private final com.wxn.chatbot.message.service.MessageReconciliationService messages;
    private final ChannelOperationsService service;
    private final AccountSessions sessions;

    public ChannelOperationsController(
        ChannelOperationsService service,
        AccountSessions sessions,
        com.wxn.chatbot.message.service.MessageReconciliationService messages
    ) {
        this.service = service;
        this.sessions = sessions;
        this.messages = messages;
    }

    @GetMapping("/diagnostic")
    public ChannelOperationsService.Diagnostic diagnostic(@PathVariable Long id) {
        return service.diagnostic(id);
    }

    public record Retry(@NotBlank @Size(max = 200) String key) {}

    @GetMapping("/webhook-inbox")
    public java.util.List<org.bson.Document> webhooks(@PathVariable Long id) {
        return service.webhookFailures(id);
    }

    @PostMapping("/webhook-inbox/retry")
    public void retry(@PathVariable Long id, @Valid @RequestBody Retry input) {
        service.retryWebhook(id, input.key());
    }

    @PostMapping("/webhooks")
    public void webhooks(
        @PathVariable Long id,
        @Valid @RequestBody Webhooks input,
        HttpServletRequest request
    ) {
        service.webhooks(
            id,
            input.operationId(),
            input.confirmation(),
            sessions.require(request.getSession(false))
        );
    }

    @PostMapping("/qr")
    public ConnectionInstructions qr(@PathVariable Long id) {
        return service.qr(id);
    }

    @PostMapping("/operation")
    public void operation(
        @PathVariable Long id,
        @Valid @RequestBody Operation input,
        HttpServletRequest request
    ) {
        service.operation(
            id,
            input.operation(),
            input.operationId(),
            input.confirmation(),
            sessions.require(request.getSession(false))
        );
    }

    @PostMapping("/remote-read-policy")
    public void policy(@PathVariable Long id, @RequestBody Policy input, HttpServletRequest request) {
        service.remoteRead(id, input.enabled(), sessions.require(request.getSession(false)));
    }

    @PostMapping("/reconcile")
    public com.wxn.chatbot.whatsapp.model.NormalizedMessage reconcile(
        @PathVariable Long id,
        @Valid @RequestBody Reconcile input,
        HttpServletRequest request
    ) {
        return messages.reconcile(
            id,
            input.messageId(),
            input.externalMessageId(),
            sessions.require(request.getSession(false))
        );
    }
}
