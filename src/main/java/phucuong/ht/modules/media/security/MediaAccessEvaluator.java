package phucuong.ht.modules.media.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.media.model.enums.MediaCategory;

/**
 * Ai được tải ảnh lên: người có quyền tạo/sửa bản ghi sẽ chứa ảnh đó. Quyền xem ảnh KHÔNG kiểm tra ở đây:
 * URL ảnh chỉ được ký khi backend trả bản ghi cho người đã qua kiểm tra quyền đọc bản ghi.
 */
@Component("mediaAccessEvaluator")
@RequiredArgsConstructor
public class MediaAccessEvaluator {

    private static final String SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    private final securityUtils securityUtils;

    public boolean canUpload(Authentication authentication, MediaCategory category) {
        if (authentication == null || category == null) {
            return false;
        }
        return switch (category) {
            case LEADER_AVATAR -> hasAny(authentication,
                    "organization.leader.create.diocese", "organization.leader.create.deanery",
                    "organization.leader.create.parish", "organization.leader.update.parish");
            // Mọi tài khoản đã đăng nhập tự đặt ảnh đại diện của mình (việc gắn vào tài khoản do MyProfileService kiểm tra).
            case ACCOUNT_AVATAR -> true;
            case CERTIFICATE_BACKGROUND -> superAdminOrAny(authentication, "training.certificate_config.");
            case BADGE_BACKGROUND -> superAdminOrAny(authentication, "training.badge_config.");
        };
    }

    private boolean superAdminOrAny(Authentication authentication, String prefix) {
        return securityUtils.hasAuthority(authentication, SUPER_ADMIN)
                || hasAny(authentication,
                prefix + "create.diocese", prefix + "create.deanery",
                prefix + "update.diocese", prefix + "update.deanery");
    }

    private boolean hasAny(Authentication authentication, String... authorities) {
        for (String authority : authorities) {
            if (securityUtils.hasAuthority(authentication, authority)) {
                return true;
            }
        }
        return false;
    }
}
