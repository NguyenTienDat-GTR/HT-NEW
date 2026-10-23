package phucuong.ht.modules.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import phucuong.ht.common.utils.AuditActorNameResolver;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.Role;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionGrantNotifierTest {

    private TrainingNotificationService notifications;
    private ToggleNotificationRecipientResolver recipients;
    private PermissionGrantNotifier notifier;

    @BeforeEach
    void setUp() {
        notifications = mock(TrainingNotificationService.class);
        recipients = mock(ToggleNotificationRecipientResolver.class);
        AuditActorNameResolver names = mock(AuditActorNameResolver.class);
        when(names.resolve("admin-1")).thenReturn("Admin Một");
        notifier = new PermissionGrantNotifier(notifications, recipients, names, new ObjectMapper());
    }

    private static Permission permission(String code, String name) {
        Permission permission = new Permission();
        permission.setPermissionCode(code);
        permission.setPermissionName(name);
        return permission;
    }

    private static Role role() {
        Role role = new Role();
        role.setRoleCode("ADMIN_DIOCESE");
        role.setRoleName("Admin giáo phận");
        return role;
    }

    @Test
    void revokingRolePermissionsSendsOneNotificationToHoldersWithTheRevokedList() throws Exception {
        when(recipients.byRole("ADMIN_DIOCESE")).thenReturn(new LinkedHashSet<>(List.of("an", "binh")));
        List<Permission> revoked = List.of(permission("a.read.diocese", "Xem A"), permission("b.update.diocese", "Sửa B"));

        notifier.notifyRoleRevoked(role(), revoked, "admin-1");

        ArgumentCaptor<TrainingNotificationCommand> command = ArgumentCaptor.forClass(TrainingNotificationCommand.class);
        verify(notifications).notifyUsers(eq(List.of("an", "binh")), command.capture());
        TrainingNotificationCommand sent = command.getValue();
        assertEquals("ROLE_PERMISSION_REVOKED", sent.eventType());
        assertEquals("Vai trò Admin giáo phận của bạn bị thu hồi 2 quyền", sent.title());
        assertTrue(sent.message().contains("bởi Admin Một"), sent.message());
        assertTrue(sent.message().contains("Xem A, Sửa B"), sent.message());
        assertEquals(2, sent.totalCount());
        JsonNode payload = new ObjectMapper().readTree(sent.payload());
        assertEquals("PERMISSION_REVOKE", payload.get("kind").asText());
        assertEquals("ROLE", payload.get("targetType").asText());
        assertEquals("Quyền vừa bị thu hồi", payload.get("heading").asText());
        assertEquals(2, payload.get("items").size());
        assertEquals("a.read.diocese", payload.get("items").get(0).get("code").asText());
    }

    @Test
    void revokingAccountPermissionsNotifiesOnlyThatAccountAndSummarisesLongLists() throws Exception {
        when(recipients.byAccount("an")).thenReturn(Set.of("an"));
        List<Permission> revoked = List.of(permission("p1", "Quyền 1"), permission("p2", "Quyền 2"), permission("p3", "Quyền 3"),
                permission("p4", "Quyền 4"), permission("p5", "Quyền 5"));

        notifier.notifyAccountRevoked("an", revoked, "admin-1");

        ArgumentCaptor<TrainingNotificationCommand> command = ArgumentCaptor.forClass(TrainingNotificationCommand.class);
        verify(notifications).notifyUsers(eq(List.of("an")), command.capture());
        TrainingNotificationCommand sent = command.getValue();
        assertEquals("ACCOUNT_PERMISSION_REVOKED", sent.eventType());
        assertEquals("Bạn bị thu hồi 5 quyền", sent.title());
        assertTrue(sent.message().contains("và 2 quyền khác"), sent.message());
        assertEquals("PERMISSION_REVOKE", new ObjectMapper().readTree(sent.payload()).get("kind").asText());
    }

    @Test
    void nothingIsSentWhenNothingWasRevokedOrNobodyHoldsTheRole() {
        when(recipients.byRole("ADMIN_DIOCESE")).thenReturn(Set.of());
        notifier.notifyRoleRevoked(role(), List.of(permission("a", "A")), "admin-1");
        notifier.notifyRoleRevoked(role(), List.of(), "admin-1");
        notifier.notifyAccountRevoked("an", List.of(), "admin-1");
        notifier.notifyAccountRevoked(null, List.of(permission("a", "A")), "admin-1");
        verify(notifications, never()).notifyUsers(any(), any());
    }

    @Test
    void expiredPermissionsAreAnnouncedWithoutNamingTheSystemAsTheActor() throws Exception {
        when(recipients.byRole("ADMIN_DIOCESE")).thenReturn(new LinkedHashSet<>(List.of("an")));
        when(recipients.byAccount("binh")).thenReturn(Set.of("binh"));

        notifier.notifyRoleExpired(role(), List.of(permission("a.read.diocese", "Xem A")));
        notifier.notifyAccountExpired("binh", List.of(permission("b.read.diocese", "Xem B"), permission("c.read.diocese", "Xem C")));

        ArgumentCaptor<TrainingNotificationCommand> command = ArgumentCaptor.forClass(TrainingNotificationCommand.class);
        verify(notifications, org.mockito.Mockito.times(2)).notifyUsers(any(), command.capture());
        TrainingNotificationCommand role = command.getAllValues().get(0);
        TrainingNotificationCommand account = command.getAllValues().get(1);
        assertEquals("ROLE_PERMISSION_EXPIRED", role.eventType());
        assertEquals("Vai trò Admin giáo phận của bạn có 1 quyền đã hết hạn", role.title());
        assertEquals("ACCOUNT_PERMISSION_EXPIRED", account.eventType());
        assertEquals("Bạn có 2 quyền đã hết hạn", account.title());
        assertFalse(account.message().contains(" bởi "), account.message());
        assertTrue(account.message().contains("Xem B, Xem C"), account.message());
        JsonNode payload = new ObjectMapper().readTree(account.payload());
        assertEquals("PERMISSION_REVOKE", payload.get("kind").asText());
        assertEquals("Quyền vừa hết hạn", payload.get("heading").asText());
        assertEquals(2, payload.get("items").size());
    }

    @Test
    void grantsKeepTheirOriginalWordingAndPayloadKind() throws Exception {
        when(recipients.byAccount("an")).thenReturn(Set.of("an"));
        notifier.notifyAccountGranted("an", List.of(permission("p1", "Quyền 1")), "admin-1");

        ArgumentCaptor<TrainingNotificationCommand> command = ArgumentCaptor.forClass(TrainingNotificationCommand.class);
        verify(notifications).notifyUsers(any(), command.capture());
        assertEquals("ACCOUNT_PERMISSION_GRANTED", command.getValue().eventType());
        assertTrue(command.getValue().message().endsWith("Chọn thông báo để xem quyền hiện có."), command.getValue().message());
        assertEquals("PERMISSION_GRANT", new ObjectMapper().readTree(command.getValue().payload()).get("kind").asText());
    }
}
