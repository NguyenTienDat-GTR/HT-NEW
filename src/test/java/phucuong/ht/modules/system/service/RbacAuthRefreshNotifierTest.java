package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RbacAuthRefreshNotifierTest {

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    @Test
    void notifyAccountShouldSendAuthRefreshEventToUserQueue() {
        RbacAuthRefreshNotifier notifier = new RbacAuthRefreshNotifier(simpMessagingTemplate);

        notifier.notifyUsers(Arrays.asList("target", "target", "", null), "ACCOUNT_ROLE_TOGGLED");

        ArgumentCaptor<RbacAuthRefreshNotifier.AuthRefreshEvent> eventCaptor =
                ArgumentCaptor.forClass(RbacAuthRefreshNotifier.AuthRefreshEvent.class);
        verify(simpMessagingTemplate).convertAndSendToUser(
                eq("target"),
                eq(RbacAuthRefreshNotifier.DESTINATION),
                eventCaptor.capture()
        );
        assertEquals("AUTH_REFRESH_REQUIRED", eventCaptor.getValue().type());
        assertEquals("ACCOUNT_ROLE_TOGGLED", eventCaptor.getValue().reason());
    }
}
