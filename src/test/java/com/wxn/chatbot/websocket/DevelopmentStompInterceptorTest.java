package com.wxn.chatbot.websocket;

import com.wxn.chatbot.config.DevelopmentAccess;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import static org.assertj.core.api.Assertions.*;

class DevelopmentStompInterceptorTest {
    private static final String KEY = "test-stomp-key-at-least-32-characters";
    private DevelopmentAccess access(String... profiles) {
        var env = new MockEnvironment(); env.setActiveProfiles(profiles);
        return new DevelopmentAccess(env, KEY, 1L);
    }
    private Message<?> frame(StompCommand command, String key, String tenant, String destination, boolean principal) {
        var headers = StompHeaderAccessor.create(command);
        headers.setSessionId("test-session");
        if (key != null) headers.setNativeHeader("X-Dev-Api-Key", key);
        if (tenant != null) headers.setNativeHeader("companyId", tenant);
        if (destination != null) headers.setDestination(destination);
        if (principal) headers.setUser(new DevStompPrincipal(1L, "test-session"));
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
    @Test void validConnectSetsServerPrincipal() {
        var interceptor = new DevelopmentStompInterceptor(access("dev"));
        var message = frame(StompCommand.CONNECT, KEY, "1", null, false);
        interceptor.preSend(message, null);
        assertThat(MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class).getUser())
                .isEqualTo(new DevStompPrincipal(1L, "test-session"));
    }
    @Test void rejectsKeyTenantAndProduction() {
        var interceptor = new DevelopmentStompInterceptor(access("dev"));
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, "wrong", "1", null, false), null)).isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, KEY, "2", null, false), null)).isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> new DevelopmentStompInterceptor(access("dev", "prod")).preSend(frame(StompCommand.CONNECT, KEY, "1", null, false), null)).isInstanceOf(MessageDeliveryException.class);
    }
    @Test void permitsOnlyExactTenantTopicAfterAuthentication() {
        var interceptor = new DevelopmentStompInterceptor(access("dev"));
        assertThat(interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, null, StompDestinations.conversations(1L), true), null)).isNotNull();
        for (String destination : new String[]{"/topic/companies/2/conversations", "/topic/companies/*/conversations", "/queue/test", "/app/test"}) {
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, null, destination, true), null)).isInstanceOf(MessageDeliveryException.class);
        }
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, null, StompDestinations.conversations(1L), false), null)).isInstanceOf(MessageDeliveryException.class);
    }
    @Test void clientsCannotPublishEvenWithValidPrincipal() {
        var interceptor = new DevelopmentStompInterceptor(access("dev"));
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, null, null, StompDestinations.conversations(1L), true), null)).isInstanceOf(MessageDeliveryException.class);
    }
}
