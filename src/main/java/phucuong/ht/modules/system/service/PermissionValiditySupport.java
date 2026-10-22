package phucuong.ht.modules.system.service;

import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.enums.PermissionRevocationReason;
import phucuong.ht.modules.system.model.enums.PermissionValidity;

import java.time.OffsetDateTime;

// Tính hiệu lực của phân quyền (vai trò / tài khoản) tại thời điểm hiện tại.
final class PermissionValiditySupport {

    private PermissionValiditySupport() {
    }

    static PermissionValidity validity(
            OffsetDateTime revokedAt,
            PermissionRevocationReason reason,
            OffsetDateTime expiresAt,
            Permission permission,
            OffsetDateTime now
    ) {
        if (revokedAt != null) {
            return reason == PermissionRevocationReason.EXPIRED ? PermissionValidity.EXPIRED : PermissionValidity.REVOKED;
        }
        // Quá hạn nhưng job chưa kịp đánh dấu: vẫn là hết hạn, không còn hiệu lực.
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            return PermissionValidity.EXPIRED;
        }
        if (permission == null || !Boolean.TRUE.equals(permission.getStatus())) {
            return PermissionValidity.PERMISSION_DISABLED;
        }
        return PermissionValidity.ACTIVE;
    }

    static boolean isPermanentlyRevoked(OffsetDateTime revokedAt, OffsetDateTime expiresAt, OffsetDateTime now) {
        return revokedAt != null || (expiresAt != null && !expiresAt.isAfter(now));
    }
}
