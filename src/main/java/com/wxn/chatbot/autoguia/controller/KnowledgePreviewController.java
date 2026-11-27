package com.wxn.chatbot.autoguia.controller;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.autoguia.service.KnowledgePreviewService;
import com.wxn.chatbot.autoguia.service.KnowledgePreviewService.Preview;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.user.entity.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/knowledge")
public class KnowledgePreviewController {

    public record PreviewInput(String question, Integer limit, Boolean generate) {}

    private final KnowledgePreviewService previewService;
    private final AccountSessions sessions;

    public KnowledgePreviewController(KnowledgePreviewService previewService, AccountSessions sessions) {
        this.previewService = previewService;
        this.sessions = sessions;
    }

    @PostMapping("/preview")
    public Preview preview(@RequestBody PreviewInput input, HttpServletRequest request) {
        var identity = sessions.require(request.getSession(false));
        if (identity.role() != UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Acesso administrativo necessário");
        }
        return previewService.preview(
            input == null ? null : input.question(),
            input == null ? null : input.limit(),
            input != null && Boolean.TRUE.equals(input.generate())
        );
    }
}
