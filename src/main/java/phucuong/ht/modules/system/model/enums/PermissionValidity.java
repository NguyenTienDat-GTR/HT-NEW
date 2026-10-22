package phucuong.ht.modules.system.model.enums;

// Hiệu lực của một phân quyền: chỉ PERMISSION_DISABLED có thể hồi phục (bật lại quyền); các trạng thái thu hồi là vĩnh viễn.
public enum PermissionValidity {
    ACTIVE, EXPIRED, REVOKED, PERMISSION_DISABLED
}
