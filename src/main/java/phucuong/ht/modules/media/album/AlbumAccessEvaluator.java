package phucuong.ht.modules.media.album;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;

/**
 * Quyền với album ảnh nền theo quyền thư viện cấu hình tương ứng (training.certificate_config.* / training.badge_config.*):
 * xem album cần quyền đọc; thêm / xóa ảnh cần quyền tạo hoặc sửa. Phạm vi (đơn vị nào) do service xác định.
 */
@Component("albumAccessEvaluator")
@RequiredArgsConstructor
public class AlbumAccessEvaluator {

    private static final String SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    private final securityUtils securityUtils;

    public boolean canRead(Authentication authentication, AlbumKind kind) {
        return has(authentication, kind, "read", "create", "update");
    }

    public boolean canWrite(Authentication authentication, AlbumKind kind) {
        return has(authentication, kind, "create", "update");
    }

    private boolean has(Authentication authentication, AlbumKind kind, String... actions) {
        if (authentication == null || kind == null) {
            return false;
        }
        if (securityUtils.hasAuthority(authentication, SUPER_ADMIN)) {
            return true;
        }
        for (String action : actions) {
            for (String scope : new String[]{"diocese", "deanery"}) {
                if (securityUtils.hasAuthority(authentication, kind.getPermissionPrefix() + action + "." + scope)) {
                    return true;
                }
            }
        }
        return false;
    }
}
