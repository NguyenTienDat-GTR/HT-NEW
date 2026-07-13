package phucuong.ht.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSocketConfigTest {

    private final WebSocketConfig.NotificationInboundChannelInterceptor interceptor =
            new WebSocketConfig.NotificationInboundChannelInterceptor();

    @Test
    void requiresAuthenticatedConnectAndOnlyAllowsPersonalNotificationSubscription() {
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message(StompCommand.CONNECT, null, false), null));

        Message<?> allowed = message(StompCommand.SUBSCRIBE, "/user/queue/notifications", true);
        assertSame(allowed, interceptor.preSend(allowed, null));

        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message(StompCommand.SUBSCRIBE, "/user/other/queue/notifications", true), null));
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message(StompCommand.SUBSCRIBE, "/topic/notifications", true), null));
    }

    @Test
    void blocksAllClientStompSendFrames() {
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message(StompCommand.SEND, "/app/notifications", true), null));
    }

    private Message<byte[]> message(StompCommand command, String destination, boolean authenticated) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        if (authenticated) {
            accessor.setUser(new UsernamePasswordAuthenticationToken("user", null));
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
