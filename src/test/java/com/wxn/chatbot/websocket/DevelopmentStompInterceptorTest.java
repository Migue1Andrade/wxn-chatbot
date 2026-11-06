package com.wxn.chatbot.websocket;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.config.DevelopmentAccess;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.mock.env.MockEnvironment;

class DevelopmentStompInterceptorTest {

    private static final String KEY = "test-stomp-key-at-least-32-characters";

    private DevelopmentAccess access(String... profiles) {
        var env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new DevelopmentAccess(env, KEY);
    }

    private Message<?> frame(StompCommand command, String key, String destination, boolean principal) {
        var headers = StompHeaderAccessor.create(command);
        headers.setSessionId("test-session");
        headers.setSessionAttributes(
            java.util.Map.of(
                AccountSessions.SOCKET_SESSION,
                new org.springframework.mock.web.MockHttpSession()
            )
        );
        if (key != null) {
            headers.setNativeHeader("X-Dev-Api-Key", key);
        }
        if (destination != null) {
            headers.setDestination(destination);
        }
        if (principal) {
            headers.setUser(new DevStompPrincipal("test-session"));
        }
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    @Test
    void validConnectSetsServerPrincipal() {
        var interceptor = new DevelopmentStompInterceptor(access("dev"), mock(AccountSessions.class));
        var message = frame(StompCommand.CONNECT, KEY, null, false);
        interceptor.preSend(message, null);
        assertThat(MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class).getUser()).isEqualTo(
            new DevStompPrincipal("test-session")
        );
    }

    @Test
    void rejectsKeyProduction() {
        var interceptor = new DevelopmentStompInterceptor(access("dev"), mock(AccountSessions.class));
        assertThatThrownBy(() ->
            interceptor.preSend(frame(StompCommand.CONNECT, "wrong", null, false), null)
        ).isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() ->
            new DevelopmentStompInterceptor(access("dev", "prod"), mock(AccountSessions.class)).preSend(
                frame(StompCommand.CONNECT, KEY, null, false),
                null
            )
        ).isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void permitsOnlyExactTopicAfterAuthentication() {
        var accounts = mock(AccountSessions.class);
        when(accounts.require(any())).thenReturn(
            new AccountSessions.Identity(
                1L,
                "Admin",
                "admin",
                com.wxn.chatbot.user.entity.UserRole.ADMIN,
                10L
            )
        );
        var interceptor = new DevelopmentStompInterceptor(access("dev"), accounts);
        assertThat(
            interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, null, StompDestinations.conversations(), true),
                null
            )
        ).isNotNull();
        for (String destination : new String[] {
            "/topic/messages",
            "/topic/*",
            "/queue/test",
            "/app/test",
        }) {
            assertThatThrownBy(() ->
                interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, destination, true), null)
            ).isInstanceOf(MessageDeliveryException.class);
        }
        assertThatThrownBy(() ->
            interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, null, StompDestinations.conversations(), false),
                null
            )
        ).isInstanceOf(MessageDeliveryException.class);
        when(accounts.require(any())).thenReturn(
            new AccountSessions.Identity(
                2L,
                "Atendente",
                "attendant",
                com.wxn.chatbot.user.entity.UserRole.ATTENDANT,
                20L
            )
        );
        assertThatThrownBy(() ->
            interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, null, StompDestinations.conversations(), true),
                null
            )
        ).isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() ->
            interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, null, "/topic/members/21/conversations", true),
                null
            )
        ).isInstanceOf(MessageDeliveryException.class);
        assertThat(
            interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, null, "/topic/members/20/conversations", true),
                null
            )
        ).isNotNull();
        assertThat(
            interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, null, "/topic/users/2/notifications", true),
                null
            )
        ).isNotNull();
        assertThatThrownBy(() ->
            interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, null, "/topic/users/1/notifications", true),
                null
            )
        ).isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void clientsCannotPublishEvenWithValidPrincipal() {
        var interceptor = new DevelopmentStompInterceptor(access("dev"), mock(AccountSessions.class));
        assertThatThrownBy(() ->
            interceptor.preSend(frame(StompCommand.SEND, null, StompDestinations.conversations(), true), null)
        ).isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void channelDiagnosticsTopicIsAdminOnly() {
        var sessions = mock(AccountSessions.class);
        var interceptor = new DevelopmentStompInterceptor(access("dev"), sessions);
        when(sessions.require(any())).thenReturn(
            new AccountSessions.Identity(
                1L,
                "Atendente",
                "att",
                com.wxn.chatbot.user.entity.UserRole.ATTENDANT,
                8L
            )
        );
        assertThatThrownBy(() ->
            interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/admin/channels", true), null)
        ).isInstanceOf(MessageDeliveryException.class);
        when(sessions.require(any())).thenReturn(
            new AccountSessions.Identity(2L, "Admin", "admin", com.wxn.chatbot.user.entity.UserRole.ADMIN, 9L)
        );
        assertThat(
            interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/admin/channels", true), null)
        ).isNotNull();
    }
}
