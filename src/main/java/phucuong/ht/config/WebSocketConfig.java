package phucuong.ht.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String NOTIFICATION_DESTINATION = "/user/queue/notifications";

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/notifications");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new NotificationInboundChannelInterceptor());
    }

    static final class NotificationInboundChannelInterceptor implements ChannelInterceptor {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (accessor == null || accessor.getCommand() == null) {
                return message;
            }

            StompCommand command = accessor.getCommand();
            if (command == StompCommand.CONNECT) {
                requireAuthenticated(accessor);
                return message;
            }
            if (command == StompCommand.SUBSCRIBE) {
                requireAuthenticated(accessor);
                if (!NOTIFICATION_DESTINATION.equals(accessor.getDestination())) {
                    throw new AccessDeniedException("Chỉ được subscribe kênh thông báo cá nhân");
                }
                return message;
            }
            if (command == StompCommand.SEND) {
                throw new AccessDeniedException("Client không được gửi STOMP message");
            }
            return message;
        }

        private void requireAuthenticated(StompHeaderAccessor accessor) {
            if (accessor.getUser() == null) {
                throw new AccessDeniedException("Yêu cầu xác thực WebSocket");
            }
        }
    }
}
