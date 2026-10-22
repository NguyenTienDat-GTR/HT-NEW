package phucuong.ht.modules.auth.model.response;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/** Một quyền đang có hiệu lực của tài khoản đang đăng nhập, kèm nguồn cấp (vai trò hoặc phân quyền riêng). */
@Getter
@Builder
public class MyPermissionResponse {
    private String permissionCode;
    private String permissionName;
    private String description;
    private String moduleName;
    private String resourceName;
    private String actionName;
    private String scopeName;
    /** ROLE: quyền từ vai trò; ACCOUNT: quyền riêng của tài khoản. */
    private String sourceType;
    private String roleCode;
    private String roleName;
    private String note;
    private String assignedByName;
    private OffsetDateTime assignedAt;
    private OffsetDateTime expiresAt;
}
