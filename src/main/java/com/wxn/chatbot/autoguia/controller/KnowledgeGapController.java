package com.wxn.chatbot.autoguia.controller;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.autoguia.service.KnowledgeArticleService;
import com.wxn.chatbot.autoguia.service.KnowledgeGapService;
import com.wxn.chatbot.autoguia.service.KnowledgeGapService.DraftConversion;
import com.wxn.chatbot.autoguia.service.KnowledgeGapService.GapPage;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.user.entity.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/knowledge/gaps")
public class KnowledgeGapController {

    private final KnowledgeGapService gaps;
    private final AccountSessions sessions;

    public KnowledgeGapController(KnowledgeGapService gaps, AccountSessions sessions) {
        this.gaps = gaps;
        this.sessions = sessions;
    }

    @GetMapping
    public GapPage page(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size,
        HttpServletRequest request
    ) {
        requireAdmin(request);
        return gaps.page(page, size);
    }

    @PostMapping("/{gapId}/draft")
    public DraftConversion convert(
        @PathVariable long gapId,
        @RequestBody KnowledgeArticleService.Input suggestion,
        HttpServletRequest request
    ) {
        var admin = requireAdmin(request);
        return gaps.convertToDraft(gapId, suggestion, admin.id());
    }

    private AccountSessions.Identity requireAdmin(HttpServletRequest request) {
        var identity = sessions.require(request.getSession(false));
        if (identity.role() != UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Acesso administrativo necessário");
        }
        return identity;
    }
}
