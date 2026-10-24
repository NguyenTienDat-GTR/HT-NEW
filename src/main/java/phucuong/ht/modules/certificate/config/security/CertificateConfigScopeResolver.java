package phucuong.ht.modules.certificate.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.AccountRepository;

import java.util.List;
import java.util.UUID;

// Xác định phạm vi sở hữu cấu hình chứng nhận của người đang thao tác.
// scopeKey: SYSTEM (super admin) | DIOCESE:<uuid> (admin giáo phận) | DEANERY:<uuid> (admin giáo hạt).
@Component
@RequiredArgsConstructor
public class CertificateConfigScopeResolver {

    public static final String SYSTEM = "SYSTEM";
    public static final String DIOCESE = "DIOCESE";
    public static final String DEANERY = "DEANERY";

    private static final String SUPER_ADMIN_ROLE = "ROLE_SUPER_ADMIN";

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final CommonValidationHelper validationHelper;

    // level: SYSTEM | DIOCESE | DEANERY; key: khóa phạm vi của người thao tác;
    // usableKeys: các phạm vi mà người này được xem/dùng cấu hình (chính nó + cấp trên + hệ thống); null = xem tất cả.
    public record ActorScope(String level, String key, List<String> usableKeys) {
        public boolean isSuperAdmin() {
            return SYSTEM.equals(level);
        }

        public boolean canSee(String scopeKey) {
            return usableKeys == null || usableKeys.contains(scopeKey);
        }

        // Chỉ sửa/xóa cấu hình thuộc phạm vi của chính mình (super admin: mọi phạm vi).
        public boolean canEdit(String scopeKey) {
            return isSuperAdmin() || key.equals(scopeKey);
        }
    }

    public static String keyOf(String level, UUID id) {
        return id == null ? SYSTEM : level + ":" + id;
    }

    public static String levelOf(String scopeKey) {
        if (scopeKey == null || SYSTEM.equals(scopeKey)) {
            return SYSTEM;
        }
        return scopeKey.startsWith(DEANERY + ":") ? DEANERY : DIOCESE;
    }

    // Phạm vi theo quyền cấu hình chứng nhận (training.certificate_config.*).
    public ActorScope resolve(Authentication authentication) {
        return resolve(authentication, "training.certificate_config.", "Bạn không có quyền với cấu hình chứng nhận");
    }

    // permissionPrefix: tiền tố quyền thư viện cấu hình, vd "training.badge_config." cho thẻ tham dự.
    public ActorScope resolve(Authentication authentication, String permissionPrefix, String forbiddenMessage) {
        if (securityUtils.hasAuthority(authentication, SUPER_ADMIN_ROLE)) {
            return new ActorScope(SYSTEM, SYSTEM, null);
        }
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            throw validationHelper.forbidden("Không xác định được phạm vi của tài khoản");
        }
        boolean diocese = hasAnyConfigAuthority(authentication, permissionPrefix, DIOCESE.toLowerCase());
        boolean deanery = hasAnyConfigAuthority(authentication, permissionPrefix, DEANERY.toLowerCase());
        if (diocese) {
            UUID dioceseId = accountRepository.findActiveDioceseIdByUsername(username)
                    .orElseThrow(() -> validationHelper.forbidden("Tài khoản chưa thuộc giáo phận nào đang hoạt động"));
            String key = keyOf(DIOCESE, dioceseId);
            return new ActorScope(DIOCESE, key, List.of(key, SYSTEM));
        }
        if (deanery) {
            UUID deaneryId = accountRepository.findActiveDeaneryIdByUsername(username)
                    .orElseThrow(() -> validationHelper.forbidden("Tài khoản chưa thuộc giáo hạt nào đang hoạt động"));
            String key = keyOf(DEANERY, deaneryId);
            // Giáo hạt dùng được cấu hình của giáo phận chủ quản (kế thừa) và của hệ thống.
            List<String> usable = accountRepository.findActiveDioceseIdByUsername(username)
                    .map(id -> List.of(key, keyOf(DIOCESE, id), SYSTEM))
                    .orElse(List.of(key, SYSTEM));
            return new ActorScope(DEANERY, key, usable);
        }
        throw validationHelper.forbidden(forbiddenMessage);
    }

    private boolean hasAnyConfigAuthority(Authentication authentication, String permissionPrefix, String scopeCode) {
        for (String action : new String[]{"read", "create", "update", "delete"}) {
            if (securityUtils.hasAuthority(authentication, permissionPrefix + action + "." + scopeCode)) {
                return true;
            }
        }
        return false;
    }
}
