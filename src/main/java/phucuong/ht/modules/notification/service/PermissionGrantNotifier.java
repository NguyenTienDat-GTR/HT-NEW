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
                permissions, actor, "ROLE", roleName);
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
                permissions, actor, "ACCOUNT", username);
    }

    private void send(List<String> recipients, String eventType, String title, String messageHead, List<Permission> permissions,
                      String actor, String targetType, String targetName) {
        List<String> names = permissions.stream().map(this::nameOf).toList();
        String actorName = actor == null || actor.isBlank() ? null : auditActorNameResolver.resolve(actor);
        StringBuilder message = new StringBuilder(messageHead);
        if (actorName != null && !actorName.isBlank()) {
            message.append(" bởi ").append(actorName);
        }
        message.append(": ").append(String.join(", ", names.subList(0, Math.min(PREVIEW_LIMIT, names.size()))));
        if (names.size() > PREVIEW_LIMIT) {
            message.append(" và ").append(names.size() - PREVIEW_LIMIT).append(" quyền khác");
        }
        message.append(". Chọn thông báo để xem quyền hiện có.");

        List<Map<String, String>> items = new ArrayList<>();
        for (Permission permission : permissions) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("label", nameOf(permission));
            item.put("detail", detailOf(permission));
            item.put("code", permission.getPermissionCode());
            items.add(item);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", "PERMISSION_GRANT");
        payload.put("targetType", targetType);
        payload.put("targetName", targetName);
        payload.put("heading", "Quyền vừa được phân");
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
