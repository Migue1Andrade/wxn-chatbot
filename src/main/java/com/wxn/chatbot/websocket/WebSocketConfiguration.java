package com.wxn.chatbot.websocket;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.config.DevelopmentAccess;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.*;
import org.springframework.messaging.simp.config.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
@Profile("dev & !prod")
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

    private final DevelopmentStompInterceptor interceptor;
    private final SubscriptionReceiptInterceptor receipts;
    private final DevelopmentAccess access;
    private final String origin;
    private final AccountSessions sessions;
    private final com.wxn.chatbot.auth.config.AccountSocketAccess socketAccess;

    public WebSocketConfiguration(
        DevelopmentStompInterceptor interceptor,
        AccountSessions sessions,
        com.wxn.chatbot.auth.config.AccountSocketAccess socketAccess,
        DevelopmentAccess access,
        SubscriptionReceiptInterceptor receipts,
        @Value("${wxn.dev.frontend-origin:http://localhost:4002}") String origin
    ) {
        this.sessions = sessions;
        this.socketAccess = socketAccess;
        this.receipts = receipts;
        this.interceptor = interceptor;
        this.access = access;
        this.origin = origin;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setPreserveReceiveOrder(true);
        registry.addEndpoint("/ws").setAllowedOrigins(origin).addInterceptors(
            new HandshakeInterceptor() {
                public boolean beforeHandshake(
                    ServerHttpRequest request,
                    ServerHttpResponse response,
                    WebSocketHandler handler,
                    Map<String, Object> attributes
                ) {
                    if (!access.enabled()) {
                        response.setStatusCode(org.springframework.http.HttpStatus.NOT_FOUND);
                    }
                    if (!access.enabled()) {
                        return false;
                    }
                    if (!(request instanceof ServletServerHttpRequest servlet)) {
                        response.setStatusCode(HttpStatus.UNAUTHORIZED);
                        return false;
                    }
                    var session = servlet.getServletRequest().getSession(false);
                    try {
                        sessions.require(session);
                    } catch (com.wxn.chatbot.common.ApiException denied) {
                        response.setStatusCode(HttpStatus.UNAUTHORIZED);
                        return false;
                    }
                    attributes.put(AccountSessions.SOCKET_SESSION, session);
                    return true;
                }

                public void afterHandshake(
                    ServerHttpRequest request,
                    ServerHttpResponse response,
                    WebSocketHandler handler,
                    Exception error
                ) {}
            }
        );
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry
            .enableSimpleBroker("/topic", "/queue")
            .setHeartbeatValue(new long[] { 10000, 10000 })
            .setTaskScheduler(stompScheduler());
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(interceptor, receipts);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(socketAccess);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
            .addDecoratorFactory(socketAccess)
            .setTimeToFirstMessage(10000)
            .setMessageSizeLimit(16384)
            .setSendBufferSizeLimit(262144)
            .setSendTimeLimit(10000);
    }

    @Bean
    public ThreadPoolTaskScheduler stompScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("wxn-stomp-");
        return scheduler;
    }
}
