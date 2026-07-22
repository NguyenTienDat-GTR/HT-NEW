package phucuong.ht.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;
import phucuong.ht.modules.system.service.RbacAuthRefreshNotifier;

import java.util.Arrays;
import java.util.Set;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String NOTIFICATION_DESTINATION = "/user/queue/notifications";
    private static final Set<String> USER_DESTINATIONS = Set.of(
            NOTIFICATION_DESTINATION,
            "/user" + RbacAuthRefreshNotifier.DESTINATION
    );

    private final jwtUtils jwtUtils;
    private final RedisTokenSessionService redisTokenSessionService;

    @Value("${app.websocket.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/notifications")
                .setAllowedOrigins(resolveAllowedOrigins());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new NotificationInboundChannelInterceptor(jwtUtils, redisTokenSessionService));
    }

    private String[] resolveAllowedOrigins() {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);
        if (origins.length == 0 || Arrays.asList(origins).contains("*")) {
            throw new IllegalStateException("APP_WEBSOCKET_ALLOWED_ORIGINS must contain exact origins");
        }
        return origins;
    }

    static final class NotificationInboundChannelInterceptor implements ChannelInterceptor {
        private final jwtUtils jwtUtils;
        private final RedisTokenSessionService redisTokenSessionService;

        NotificationInboundChannelInterceptor() {
            this(null, null);
        }

        NotificationInboundChannelInterceptor(jwtUtils jwtUtils, RedisTokenSessionService redisTokenSessionService) {
            this.jwtUtils = jwtUtils;
            this.redisTokenSessionService = redisTokenSessionService;
        }

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (accessor == null || accessor.getCommand() == null) {
                return message;
            }

            StompCommand command = accessor.getCommand();
            if (command == StompCommand.CONNECT) {
                authenticateConnect(accessor);
                return message;
            }
            if (command == StompCommand.SUBSCRIBE) {
                requireAuthenticated(accessor);
                if (!USER_DESTINATIONS.contains(accessor.getDestination())) {
                    throw new AccessDeniedException("Chỉ được subscribe kênh thông báo cá nhân");
                }
                return message;
            }
            if (command == StompCommand.SEND) {
                throw new AccessDeniedException("Client không được gửi STOMP message");
            }
            return message;
        }

        private void authenticateConnect(StompHeaderAccessor accessor) {
            String authorization = accessor.getFirstNativeHeader("Authorization");
            if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
                throw new AccessDeniedException("Yêu cầu Bearer access token cho WebSocket");
            }
            if (jwtUtils == null || redisTokenSessionService == null) {
                throw new AccessDeniedException("WebSocket chưa sẵn sàng xác thực token");
            }

            jwtUtils.TokenClaims claims = jwtUtils.parseAccessTokenClaims(authorization.substring(7));
            if (claims.principal().isMustChangePassword()) {
                throw new AccessDeniedException("Phải đổi mật khẩu trước khi mở WebSocket");
            }
            if (!redisTokenSessionService.isAccessTokenActive(
                    claims.sessionId(),
                    claims.principal().getUsername(),
                    claims.tokenId()
            )) {
                throw new AccessDeniedException("Phiên WebSocket không còn hiệu lực");
            }
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    claims.principal(),
                    null,
                    claims.principal().getAuthorities()
            ));
        }

        private void requireAuthenticated(StompHeaderAccessor accessor) {
            if (accessor.getUser() == null) {
                throw new AccessDeniedException("Yêu cầu xác thực WebSocket");
            }
        }
    }
}
