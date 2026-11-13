package phucuong.ht.modules.system.service;

import org.springframework.security.core.Authentication;
import phucuong.ht.common.security.AccountScopeResolver;
import phucuong.ht.modules.auth.repository.AccountRepository;

import java.util.UUID;

/**
 * Quyền sở hữu vai trò / tài khoản theo người tạo (created_by):
 * - SUPER_ADMIN chỉ quản lý dữ liệu hệ thống hoặc do super-admin tạo;
 * - các cấp giáo phận / giáo hạt / giáo xứ chỉ quản lý dữ liệu do chính tài khoản hoặc cấp đó tạo.
 */
final class ManagementOwnership {

    private ManagementOwnership() {
    }

    static boolean isSuperAdminOwned(String createdBy) {
        return createdBy != null && ("super-admin".equalsIgnoreCase(createdBy.trim()) || "system".equalsIgnoreCase(createdBy.trim()));
    }

    /**
     * Người tạo là chính tài khoản đang thao tác, hoặc là tài khoản cùng cấp trong cùng đơn vị
     * (cùng giáo phận / giáo hạt / giáo xứ). Cấp của người thao tác lấy từ quyền; đơn vị của hai bên suy ra từ phạm vi hiệu lực
     * (gắn trực tiếp hoặc qua giáo xứ / huynh trưởng), cùng cấp nghĩa là cùng bậc vai trò cao nhất.
     */
    static boolean isOwnedByActorScope(AccountRepository accountRepository, Authentication authentication, String actorUsername, String actorAuditActor, String createdBy) {
        if (createdBy == null || createdBy.isBlank()) {
            return false;
        }
        String creator = createdBy.trim();
        if (creator.equalsIgnoreCase(actorUsername) || creator.equalsIgnoreCase(actorAuditActor)) {
            return true;
        }
        if (actorUsername == null || accountRepository == null) {
            return false;
        }
        AccountScopeResolver.Level level = AccountScopeResolver.levelOf(authentication);
        if (level == null || !accountRepository.existsByUsername(creator)) {
            return false;
        }
        UUID actorUnit = unitAt(accountRepository, actorUsername, level);
        Integer actorTier = accountRepository.findHighestActiveRoleOrder(actorUsername);
        return actorUnit != null
                && actorTier != null
                && actorUnit.equals(unitAt(accountRepository, creator, level))
                && actorTier.equals(accountRepository.findHighestActiveRoleOrder(creator));
    }

    private static UUID unitAt(AccountRepository accountRepository, String username, AccountScopeResolver.Level level) {
        return switch (level) {
            case DIOCESE -> accountRepository.findActiveDioceseIdByUsername(username).orElse(null);
            case DEANERY -> accountRepository.findActiveDeaneryIdByUsername(username).orElse(null);
            case PARISH -> accountRepository.findActiveParishIdByUsername(username).orElse(null);
        };
    }
}
