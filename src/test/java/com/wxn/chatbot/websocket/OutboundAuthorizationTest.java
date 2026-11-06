package com.wxn.chatbot.websocket;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wxn.chatbot.auth.config.AccountSocketAccess;
import com.wxn.chatbot.auth.service.*;
import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.socket.*;

class OutboundAuthorizationTest {

    @Test
    void revokedAssignmentSuppressesFramesEvenOnOldSubscription() throws Exception {
        var sessions = mock(AccountSessions.class);
        var authorization = mock(ConversationAuthorization.class);
        var filter = new AccountSocketAccess(sessions, authorization, new ObjectMapper());
        var actor = new AccountSessions.Identity(1L, "Attendant", "fixture", UserRole.ATTENDANT, 8L);
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("socket-fixture");
        when(socket.getAttributes()).thenReturn(
            java.util.Map.of(AccountSessions.SOCKET_SESSION, new MockHttpSession())
        );
        when(sessions.require(any())).thenReturn(actor);
        filter.decorate(mock(WebSocketHandler.class)).afterConnectionEstablished(socket);
        doThrow(new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "Transferido"))
            .when(authorization)
            .require("0123456789abcdef01234567", actor);
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId("socket-fixture");
        headers.setDestination("/topic/members/8/conversations");
        var frame = MessageBuilder.createMessage(
            "{\"type\":\"message.received\",\"conversationId\":\"0123456789abcdef01234567\"}".getBytes(
                java.nio.charset.StandardCharsets.UTF_8
            ),
            headers.getMessageHeaders()
        );
        assertThat(filter.preSend(frame, null)).isNull();
        headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId("socket-fixture");
        headers.setDestination("/topic/admin/channels");
        assertThat(
            filter.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null)
        ).isNull();
    }
}
