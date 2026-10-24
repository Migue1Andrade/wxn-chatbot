package com.wxn.chatbot.conversation.controller;

import com.wxn.chatbot.conversation.dto.ConversationManagementDtos.*;
import com.wxn.chatbot.conversation.dto.ConversationPage;
import com.wxn.chatbot.conversation.dto.ConversationQuery;
import com.wxn.chatbot.conversation.model.ConversationStatus;
import com.wxn.chatbot.conversation.service.*;
import com.wxn.chatbot.whatsapp.model.NormalizedConversation;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/conversations")
public class ConversationController {

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
        ConversationReadService reads
    ) {
        this.service = service;
        this.queries = queries;
        this.management = management;
        this.activities = activities;
        this.reads = reads;
    }

    @GetMapping(params = "!paged")
    public List<NormalizedConversation> list(
        @PathVariable Long companyId,
        @Valid @ModelAttribute ConversationQuery query
    ) {
        return queries.list(companyId, query);
    }

    @GetMapping(params = "paged")
    public ConversationPage page(
        @PathVariable Long companyId,
        @Valid @ModelAttribute ConversationQuery query
    ) {
        return queries.page(companyId, query);
    }

    @PatchMapping("/{conversationId}/assume")
    public NormalizedConversation assume(
        @PathVariable Long companyId,
        @PathVariable String conversationId,
        @Valid @RequestBody Action request
    ) {
        return management.assume(companyId, conversationId, request);
    }

    @GetMapping("/{conversationId}")
    public NormalizedConversation get(@PathVariable Long companyId, @PathVariable String conversationId) {
        return service.get(companyId, conversationId);
    }

    @PatchMapping("/{conversationId}/assign")
    public NormalizedConversation assign(
        @PathVariable Long companyId,
        @PathVariable String conversationId,
        @Valid @RequestBody Assign request
    ) {
        return management.assign(companyId, conversationId, request);
    }

    @PatchMapping("/{conversationId}/transfer")
    public NormalizedConversation transfer(
        @PathVariable Long companyId,
        @PathVariable String conversationId,
        @Valid @RequestBody Assign request
    ) {
        return management.transfer(companyId, conversationId, request);
    }

    @PatchMapping("/{conversationId}/close")
    public NormalizedConversation close(
        @PathVariable Long companyId,
        @PathVariable String conversationId,
        @Valid @RequestBody Action request
    ) {
        return management.close(companyId, conversationId, request);
    }

    @PatchMapping("/{conversationId}/reopen")
    public NormalizedConversation reopen(
        @PathVariable Long companyId,
        @PathVariable String conversationId,
        @Valid @RequestBody Action request
    ) {
        return management.reopen(companyId, conversationId, request);
    }

    @GetMapping("/{conversationId}/events")
    public List<Activity> events(
        @PathVariable Long companyId,
        @PathVariable String conversationId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "100") int size
    ) {
        return activities.list(service.require(companyId, conversationId), page, size);
    }

    @PostMapping("/{conversationId}/read")
    public ReadUpdate read(
        @PathVariable Long companyId,
        @PathVariable String conversationId,
        @Valid @RequestBody Read request
    ) {
        return reads.mark(companyId, conversationId, request);
    }
}
