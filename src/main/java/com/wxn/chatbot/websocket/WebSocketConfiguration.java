package com.wxn.chatbot.websocket;

import com.wxn.chatbot.config.DevelopmentAccess;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
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

    public WebSocketConfiguration(
        DevelopmentStompInterceptor interceptor,
        DevelopmentAccess access,
        SubscriptionReceiptInterceptor receipts,
        @Value("${wxn.dev.frontend-origin:http://localhost:4002}") String origin
    ) {
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
                    if (!access.enabled()) response.setStatusCode(
                        org.springframework.http.HttpStatus.NOT_FOUND
                    );
                    return access.enabled();
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
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
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
