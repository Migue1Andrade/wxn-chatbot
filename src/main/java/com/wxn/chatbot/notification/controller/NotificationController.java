package com.wxn.chatbot.notification.controller;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;
    private final AccountSessions sessions;
    private final com.wxn.chatbot.conversation.service.ConversationManagementService management;
    private final com.wxn.chatbot.conversation.service.ConversationService conversations;

    public NotificationController(
        NotificationService service,
        AccountSessions sessions,
        com.wxn.chatbot.conversation.service.ConversationManagementService management,
        com.wxn.chatbot.conversation.service.ConversationService conversations
    ) {
        this.service = service;
        this.sessions = sessions;
        this.management = management;
        this.conversations = conversations;
    }

    private Long user(HttpServletRequest r) {
        return sessions.require(r.getSession(false)).id();
    }

    @GetMapping
    public java.util.List<NotificationService.Response> list(
        HttpServletRequest r,
        @RequestParam(defaultValue = "0") int page
    ) {
        return service.list(user(r), page);
    }

    @GetMapping("/unread-count")
    public java.util.Map<String, Long> count(HttpServletRequest r) {
        return java.util.Map.of("count", service.count(user(r)));
    }

    @PatchMapping("/{id}/read")
    public void read(@PathVariable Long id, HttpServletRequest r) {
        service.read(user(r), id);
    }

    @PostMapping("/read-all")
    public void readAll(HttpServletRequest r) {
        service.readAll(user(r));
    }

    @PostMapping("/{id}/claim")
    public com.wxn.chatbot.whatsapp.model.NormalizedConversation claim(
        @PathVariable Long id,
        HttpServletRequest r
    ) {
        var identity = sessions.require(r.getSession(false));
        if (identity.memberId() == null) {
            throw new com.wxn.chatbot.common.ApiException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Somente atendentes podem reivindicar um atendimento disponível"
            );
        }
        var notification = service.claimResource(identity.id(), id);
        var current = conversations.require(notification.resourceId());
        return management.assume(
            notification.resourceId(),
            new com.wxn.chatbot.conversation.dto.ConversationManagementDtos.Action(
                identity.memberId(),
                current.getVersion(),
                "Atendimento assumido pela notificação"
            )
        );
    }
}
