package com.wxn.chatbot.autoguia.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.autoguia.service.KnowledgePreviewService;
import com.wxn.chatbot.autoguia.service.KnowledgePreviewService.Preview;
import com.wxn.chatbot.user.entity.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgePreviewControllerTest {

    @Mock
    private KnowledgePreviewService previewService;

    @Mock
    private AccountSessions sessions;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpSession session;

    private KnowledgePreviewController controller;

    @BeforeEach
    void setUp() {
        controller = new KnowledgePreviewController(previewService, sessions);
        when(request.getSession(false)).thenReturn(session);
        when(sessions.require(session)).thenReturn(
            new AccountSessions.Identity(1L, "Fixture admin", "admin", UserRole.ADMIN, null)
        );
    }

    @Test
    void passesExplicitGenerationRequestAndReturnsGeneratedPreview() {
        var expected = new Preview("TEXT", List.of(), "GENERATED", "Fixture answer");
        when(previewService.preview("Question", 4, true)).thenReturn(expected);

        Preview result = controller.preview(
            new KnowledgePreviewController.PreviewInput("Question", 4, true),
            request
        );

        assertThat(result).isEqualTo(expected);
        verify(previewService).preview("Question", 4, true);
    }

    @Test
    void keepsGenerationOptInWhenRequestOmitsFlag() {
        var expected = new Preview("TEXT", List.of(), "NOT_REQUESTED", null);
        when(previewService.preview("Question", null, false)).thenReturn(expected);

        Preview result = controller.preview(
            new KnowledgePreviewController.PreviewInput("Question", null, null),
            request
        );

        assertThat(result).isEqualTo(expected);
        verify(previewService).preview("Question", null, false);
    }
}
