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
import jakarta.servlet.http.Cookie;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import phucuong.ht.common.utils.jwtUtils;
import phucuong.ht.modules.auth.config.AuthProperties;
import java.util.Map;
import phucuong.ht.modules.auth.service.RedisTokenSessionService;
import phucuong.ht.modules.certificate.service.CertificateRealtimeNotifier;
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
            "/user" + RbacAuthRefreshNotifier.DESTINATION,
            CertificateRealtimeNotifier.DESTINATION
    );

    private final jwtUtils jwtUtils;
    private final RedisTokenSessionService redisTokenSessionService;
    private final AuthProperties authProperties;

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
                .setAllowedOriginPatterns(resolveAllowedOrigins())
                .addInterceptors(new AccessCookieHandshakeInterceptor(authProperties.getCookie().getAccessTokenName()));
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
        if (origins.length == 0 || Arrays.stream(origins).anyMatch(WebSocketConfig::isUnsafeOrigin)) {
            throw new IllegalStateException(
                    "APP_WEBSOCKET_ALLOWED_ORIGINS must contain exact origins or https://*.<domain> subdomain patterns");
        }
        return origins;
    }

    /** Chỉ cho phép origin chính xác hoặc mẫu subdomain dạng scheme://*.domain; cấm "*" và mẫu rộng như https://*. */
    static boolean isUnsafeOrigin(String origin) {
        if (!origin.contains("*")) {
            return false;
        }
        return !origin.matches("^https?://\\*\\.[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+(:\\d+)?$");
    }

    /** Lưu access token từ cookie HttpOnly của handshake để STOMP CONNECT dùng khi FE không giữ token trong JS. */
    static final class AccessCookieHandshakeInterceptor implements HandshakeInterceptor {
        static final String ATTRIBUTE = "accessTokenCookie";
        private final String cookieName;

        AccessCookieHandshakeInterceptor(String cookieName) {
            this.cookieName = cookieName;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            if (request instanceof ServletServerHttpRequest servletRequest) {
                Cookie[] cookies = servletRequest.getServletRequest().getCookies();
                if (cookies != null) {
                    for (Cookie cookie : cookies) {
                        if (cookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                            attributes.put(ATTRIBUTE, cookie.getValue());
                            break;
                        }
                    }
                }
            }
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }
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
            String token = null;
            if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
                token = authorization.substring(7);
            } else if (accessor.getSessionAttributes() != null
                    && accessor.getSessionAttributes().get(AccessCookieHandshakeInterceptor.ATTRIBUTE) instanceof String cookieToken) {
                token = cookieToken;
            }
            if (!StringUtils.hasText(token)) {
                throw new AccessDeniedException("Yêu cầu access token cho WebSocket");
            }
            if (jwtUtils == null || redisTokenSessionService == null) {
                throw new AccessDeniedException("WebSocket chưa sẵn sàng xác thực token");
            }

            jwtUtils.TokenClaims claims = jwtUtils.parseAccessTokenClaims(token);
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
