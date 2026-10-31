package phucuong.ht.common.security;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.notification.service.ToggleNotificationDispatcher;
import phucuong.ht.modules.system.service.RbacAuthRefreshNotifier;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class UnitToggleSupportTest {

    @Test
    void noopSupportSkipsEveryOptionalStep() {
        UnitToggleSupport support = UnitToggleSupport.noop();
        UUID id = UUID.randomUUID();

        support.requireOperational(UnitType.PARISH, id);
        support.requireToggleAllowed(UnitType.PARISH, id);
        assertTrue(support.recipients(UnitType.PARISH, id).isEmpty());
        assertNull(support.begin(UnitType.PARISH, id, "ly do"));
        support.finish(null, UnitType.PARISH, id, "admin");
        support.announce(Set.of("a"), "Giáo xứ", "An Phú", true, "admin");
        assertTrue(support.history(UnitType.PARISH, id, 0, 10).isEmpty());
        assertThrows(ApiException.class, () -> support.impact(UnitType.PARISH, id));
    }

    @Test
    void announceRefreshesSessionsBeforeDispatchingNotification() {
        UnitToggleSupport support = UnitToggleSupport.noop();
        RbacAuthRefreshNotifier refresh = mock(RbacAuthRefreshNotifier.class);
        ToggleNotificationDispatcher dispatcher = mock(ToggleNotificationDispatcher.class);
        ReflectionTestUtils.setField(support, "authRefreshNotifier", refresh);
        ReflectionTestUtils.setField(support, "dispatcher", dispatcher);
        Set<String> recipients = Set.of("u1");

        support.announce(recipients, "Giáo phận", "  Sài Gòn ", false, "admin");

        InOrder order = inOrder(refresh, dispatcher);
        order.verify(refresh).notifyUsers(recipients, "UNIT_STATUS_TOGGLED");
        order.verify(dispatcher).dispatch(recipients, "UNIT_STATUS_TOGGLED", "Giáo phận Sài Gòn", false, "admin");
    }

    @Test
    void labelFallsBackToTypeWhenNameIsBlank() {
        assertEquals("Giáo hạt", UnitToggleSupport.unitLabel("Giáo hạt", "  "));
        assertEquals("Giáo hạt", UnitToggleSupport.unitLabel("Giáo hạt", null));
        assertEquals("Giáo hạt Tân Bình", UnitToggleSupport.unitLabel("Giáo hạt", " Tân Bình "));
    }
}
