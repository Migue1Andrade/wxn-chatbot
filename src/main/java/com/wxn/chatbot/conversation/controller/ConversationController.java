package com.wxn.chatbot.conversation.controller;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.conversation.dto.ConversationManagementDtos.*;
import com.wxn.chatbot.conversation.dto.ConversationPage;
import com.wxn.chatbot.conversation.dto.ConversationQuery;
import com.wxn.chatbot.conversation.service.*;
import com.wxn.chatbot.whatsapp.model.NormalizedConversation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final AccountSessions sessions;
    private final HttpServletRequest httpRequest;
    private final ConversationService service;
    private final ConversationQueryService queries;
    private final ConversationManagementService management;
    private final ConversationActivityService activities;
    private final ConversationReadService reads;

    public ConversationController(
        ConversationService service,
        ConversationQueryService queries,
        ConversationManagementService management,
        ConversationActivityService activities,
        ConversationReadService reads,
        AccountSessions sessions,
        HttpServletRequest httpRequest
    ) {
        this.sessions = sessions;
        this.httpRequest = httpRequest;
        this.service = service;
        this.queries = queries;
        this.management = management;
        this.activities = activities;
        this.reads = reads;
    }

    @GetMapping(params = "!paged")
    public List<NormalizedConversation> list(@Valid @ModelAttribute ConversationQuery query) {
        if (query.readerMemberId() != null) {
            sessions.requireActor(httpRequest, query.readerMemberId());
        }
        return queries.list(scoped(query));
    }

    @GetMapping(params = "paged")
    public ConversationPage page(@Valid @ModelAttribute ConversationQuery query) {
        if (query.readerMemberId() != null) {
            sessions.requireActor(httpRequest, query.readerMemberId());
        }
        return queries.page(scoped(query));
    }

    private ConversationQuery scoped(ConversationQuery query) {
        var user = sessions.require(httpRequest.getSession(false));
        if (user.role() == com.wxn.chatbot.user.entity.UserRole.ADMIN) {
            return query;
        }
        if (user.memberId() == null) {
            throw new com.wxn.chatbot.common.ApiException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Membro humano obrigatório"
            );
        }
        return new ConversationQuery(
            query.page(),
            query.size(),
            query.status(),
            user.memberId(),
            false,
            user.memberId(),
            query.search()
        );
    }

    @PatchMapping("/{conversationId}/status")
    public NormalizedConversation status(
        @PathVariable String conversationId,
        @RequestParam com.wxn.chatbot.conversation.model.ConversationStatus status,
        @Valid @RequestBody Action request
    ) {
        sessions.requireActor(httpRequest, request.actorMemberId());
        return management.status(conversationId, request, status);
    }

    @PatchMapping("/{conversationId}/assume")
    public NormalizedConversation assume(
        @PathVariable String conversationId,
        @Valid @RequestBody Action request
    ) {
        sessions.requireActor(httpRequest, request.actorMemberId());
        return management.assume(conversationId, request);
    }

    @GetMapping("/{conversationId}")
    public NormalizedConversation get(@PathVariable String conversationId) {
        return service.get(conversationId);
    }

    @PatchMapping("/{conversationId}/assign")
    public NormalizedConversation assign(
        @PathVariable String conversationId,
        @Valid @RequestBody Assign request
    ) {
        sessions.requireActor(httpRequest, request.actorMemberId());
        return management.assign(conversationId, request);
    }

    @PatchMapping("/{conversationId}/transfer")
    public NormalizedConversation transfer(
        @PathVariable String conversationId,
        @Valid @RequestBody Assign request
    ) {
        sessions.requireActor(httpRequest, request.actorMemberId());
        return management.transfer(conversationId, request);
    }

    @PatchMapping("/{conversationId}/close")
    public NormalizedConversation close(
        @PathVariable String conversationId,
        @Valid @RequestBody Action request
    ) {
        sessions.requireActor(httpRequest, request.actorMemberId());
        return management.close(conversationId, request);
    }

    @PatchMapping("/{conversationId}/reopen")
    public NormalizedConversation reopen(
        @PathVariable String conversationId,
        @Valid @RequestBody Action request
    ) {
        sessions.requireActor(httpRequest, request.actorMemberId());
        return management.reopen(conversationId, request);
    }

    @GetMapping("/{conversationId}/events")
    public List<Activity> events(
        @PathVariable String conversationId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "100") int size,
        @RequestParam(defaultValue = "ASC") org.springframework.data.domain.Sort.Direction order
    ) {
        if (order == org.springframework.data.domain.Sort.Direction.DESC) {
            return activities.recent(service.require(conversationId), page, size);
        }
        return activities.list(service.require(conversationId), page, size);
    }

    @PostMapping("/{conversationId}/read")
    public ReadUpdate read(@PathVariable String conversationId, @Valid @RequestBody Read request) {
        sessions.requireActor(httpRequest, request.memberId());
        return reads.mark(conversationId, request);
    }
}
