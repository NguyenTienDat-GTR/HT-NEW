package phucuong.ht.modules.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import phucuong.ht.common.utils.AuditActorNameResolver;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.Role;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Mỗi request phân quyền chỉ gửi một thông báo kèm danh sách quyền vừa được phân, và chỉ tới đúng tài khoản/vai trò được phân quyền.
@Service
@RequiredArgsConstructor
public class PermissionGrantNotifier {

    public static final String ROLE_EVENT = "ROLE_PERMISSION_GRANTED";
    public static final String ACCOUNT_EVENT = "ACCOUNT_PERMISSION_GRANTED";
    public static final String ROLE_REVOKED_EVENT = "ROLE_PERMISSION_REVOKED";
    public static final String ACCOUNT_REVOKED_EVENT = "ACCOUNT_PERMISSION_REVOKED";
    public static final String ROLE_EXPIRED_EVENT = "ROLE_PERMISSION_EXPIRED";
    public static final String ACCOUNT_EXPIRED_EVENT = "ACCOUNT_PERMISSION_EXPIRED";
    private static final int PREVIEW_LIMIT = 3;

    private final TrainingNotificationService trainingNotificationService;
    private final ToggleNotificationRecipientResolver recipientResolver;
    private final AuditActorNameResolver auditActorNameResolver;
    private final ObjectMapper objectMapper;

    /** Tài khoản đang giữ vai trò nhận thông báo; các tài khoản khác không nhận. */
    public void notifyRoleGranted(Role role, List<Permission> permissions, String actor) {
        if (role == null || permissions == null || permissions.isEmpty()) {
            return;
        }
        Set<String> recipients = recipientResolver.byRole(role.getRoleCode());
        if (recipients.isEmpty()) {
            return;
        }
        String roleName = role.getRoleName() == null || role.getRoleName().isBlank() ? role.getRoleCode() : role.getRoleName();
        send(List.copyOf(recipients), ROLE_EVENT,
                "Vai trò " + roleName + " của bạn được phân thêm " + permissions.size() + " quyền",
                "Vai trò " + roleName + " vừa được phân thêm " + permissions.size() + " quyền",
                permissions, actor, "ROLE", roleName, GRANT);
    }

    /** Thu hồi quyền của vai trò: tài khoản đang giữ vai trò nhận một thông báo kèm danh sách quyền vừa bị thu hồi. */
    public void notifyRoleRevoked(Role role, List<Permission> permissions, String actor) {
        if (role == null || permissions == null || permissions.isEmpty()) {
            return;
        }
        Set<String> recipients = recipientResolver.byRole(role.getRoleCode());
        if (recipients.isEmpty()) {
            return;
        }
        String roleName = role.getRoleName() == null || role.getRoleName().isBlank() ? role.getRoleCode() : role.getRoleName();
        send(List.copyOf(recipients), ROLE_REVOKED_EVENT,
                "Vai trò " + roleName + " của bạn bị thu hồi " + permissions.size() + " quyền",
                "Vai trò " + roleName + " vừa bị thu hồi " + permissions.size() + " quyền",
                permissions, actor, "ROLE", roleName, REVOKE);
    }

    /** Quyền của vai trò tự thu hồi do hết hạn: tài khoản đang giữ vai trò nhận một thông báo kèm danh sách quyền hết hạn. */
    public void notifyRoleExpired(Role role, List<Permission> permissions) {
        if (role == null || permissions == null || permissions.isEmpty()) {
            return;
        }
        Set<String> recipients = recipientResolver.byRole(role.getRoleCode());
        if (recipients.isEmpty()) {
            return;
        }
        String roleName = role.getRoleName() == null || role.getRoleName().isBlank() ? role.getRoleCode() : role.getRoleName();
        send(List.copyOf(recipients), ROLE_EXPIRED_EVENT,
                "Vai trò " + roleName + " của bạn có " + permissions.size() + " quyền đã hết hạn",
                "Vai trò " + roleName + " vừa có " + permissions.size() + " quyền hết hạn và bị thu hồi",
                permissions, null, "ROLE", roleName, EXPIRE);
    }

    /** Quyền riêng của tài khoản tự thu hồi do hết hạn: chỉ tài khoản đó nhận thông báo. */
    public void notifyAccountExpired(String username, List<Permission> permissions) {
        if (username == null || permissions == null || permissions.isEmpty()) {
            return;
        }
        Set<String> recipients = recipientResolver.byAccount(username);
        if (recipients.isEmpty()) {
            return;
        }
        send(List.copyOf(recipients), ACCOUNT_EXPIRED_EVENT,
                "Bạn có " + permissions.size() + " quyền đã hết hạn",
                "Tài khoản của bạn vừa có " + permissions.size() + " quyền hết hạn và bị thu hồi",
                permissions, null, "ACCOUNT", username, EXPIRE);
    }

    /** Thu hồi quyền riêng của tài khoản: chỉ tài khoản đó nhận thông báo. */
    public void notifyAccountRevoked(String username, List<Permission> permissions, String actor) {
        if (username == null || permissions == null || permissions.isEmpty()) {
            return;
        }
        Set<String> recipients = recipientResolver.byAccount(username);
        if (recipients.isEmpty()) {
            return;
        }
        send(List.copyOf(recipients), ACCOUNT_REVOKED_EVENT,
                "Bạn bị thu hồi " + permissions.size() + " quyền",
                "Tài khoản của bạn vừa bị thu hồi " + permissions.size() + " quyền",
                permissions, actor, "ACCOUNT", username, REVOKE);
    }

    /** Chỉ tài khoản được phân quyền riêng nhận thông báo. */
    public void notifyAccountGranted(String username, List<Permission> permissions, String actor) {
        if (username == null || permissions == null || permissions.isEmpty()) {
            return;
        }
        Set<String> recipients = recipientResolver.byAccount(username);
        if (recipients.isEmpty()) {
            return;
        }
        send(List.copyOf(recipients), ACCOUNT_EVENT,
                "Bạn được phân thêm " + permissions.size() + " quyền",
                "Tài khoản của bạn vừa được phân thêm " + permissions.size() + " quyền",
                permissions, actor, "ACCOUNT", username, GRANT);
    }

    // Kiểu nội dung: payload.kind để giao diện chọn đúng modal chi tiết, heading là tiêu đề danh sách quyền.
    private record Kind(String payloadKind, String heading, String closing, boolean showActor) {
    }

    private static final Kind GRANT = new Kind("PERMISSION_GRANT", "Quyền vừa được phân", "Chọn thông báo để xem quyền hiện có.", true);
    private static final Kind REVOKE = new Kind("PERMISSION_REVOKE", "Quyền vừa bị thu hồi", "Chọn thông báo để xem danh sách quyền bị thu hồi.", true);
    // Hết hạn do hệ thống tự thu hồi: không ghi người thực hiện; giao diện dùng chung modal danh sách quyền bị thu hồi.
    private static final Kind EXPIRE = new Kind("PERMISSION_REVOKE", "Quyền vừa hết hạn", "Chọn thông báo để xem danh sách quyền hết hạn.", false);

    private void send(List<String> recipients, String eventType, String title, String messageHead, List<Permission> permissions,
                      String actor, String targetType, String targetName, Kind kind) {
        List<String> names = permissions.stream().map(this::nameOf).toList();
        String actorName = actor == null || actor.isBlank() ? null : auditActorNameResolver.resolve(actor);
        StringBuilder message = new StringBuilder(messageHead);
        if (kind.showActor() && actorName != null && !actorName.isBlank()) {
            message.append(" bởi ").append(actorName);
        }
        message.append(": ").append(String.join(", ", names.subList(0, Math.min(PREVIEW_LIMIT, names.size()))));
        if (names.size() > PREVIEW_LIMIT) {
            message.append(" và ").append(names.size() - PREVIEW_LIMIT).append(" quyền khác");
        }
        message.append(". ").append(kind.closing());

        List<Map<String, String>> items = new ArrayList<>();
        for (Permission permission : permissions) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("label", nameOf(permission));
            item.put("detail", detailOf(permission));
            item.put("code", permission.getPermissionCode());
            items.add(item);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", kind.payloadKind());
        payload.put("targetType", targetType);
        payload.put("targetName", targetName);
        payload.put("heading", kind.heading());
        payload.put("items", items);

        trainingNotificationService.notifyUsers(recipients, TrainingNotificationCommand.builder()
                .eventType(eventType)
                .title(title)
                .message(message.toString())
                .totalCount(permissions.size())
                .payload(toJson(payload))
                .actor(actor)
                .build());
    }

    private String nameOf(Permission permission) {
        String name = permission.getPermissionName();
        return name == null || name.isBlank() ? permission.getPermissionCode() : name;
    }

    // Module · Tài nguyên · Hành động · Phạm vi (tên tiếng Việt, không phải mã).
    private String detailOf(Permission permission) {
        List<String> parts = new ArrayList<>();
        if (permission.getModule() != null && permission.getModule().getModuleName() != null) {
            parts.add(permission.getModule().getModuleName());
        }
        if (permission.getResource() != null && permission.getResource().getResourceName() != null) {
            parts.add(permission.getResource().getResourceName());
        }
        if (permission.getAction() != null && permission.getAction().getActionName() != null) {
            parts.add(permission.getAction().getActionName());
        }
        if (permission.getScope() != null && permission.getScope().getScopeName() != null) {
            parts.add(permission.getScope().getScopeName());
        }
        return String.join(" · ", parts);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không tạo được dữ liệu chi tiết của thông báo", ex);
        }
    }
}
