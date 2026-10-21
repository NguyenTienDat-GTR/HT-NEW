package phucuong.ht.modules.system.service;

import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.system.model.entity.Account;

import java.util.Objects;
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
     * (cùng giáo phận / giáo hạt / giáo xứ).
     */
    static boolean isOwnedByActorScope(AccountRepository accountRepository, String actorUsername, String actorAuditActor, String createdBy) {
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
        Account actor = accountRepository.findById(actorUsername).orElse(null);
        Account creatorAccount = accountRepository.findById(creator).orElse(null);
        if (actor == null || creatorAccount == null) {
            return false;
        }
        UUID dioceseId = idOf(actor.getDiocese());
        UUID deaneryId = idOf(actor.getDeanery());
        UUID parishId = idOf(actor.getParish());
        if (dioceseId == null && deaneryId == null && parishId == null) {
            return false;
        }
        return Objects.equals(dioceseId, idOf(creatorAccount.getDiocese()))
                && Objects.equals(deaneryId, idOf(creatorAccount.getDeanery()))
                && Objects.equals(parishId, idOf(creatorAccount.getParish()));
    }

    private static UUID idOf(Object unit) {
        if (unit instanceof phucuong.ht.modules.diocese.model.entity.Diocese diocese) return diocese.getId();
        if (unit instanceof phucuong.ht.modules.deanery.model.entity.Deanery deanery) return deanery.getId();
        if (unit instanceof phucuong.ht.modules.parish.model.entity.Parish parish) return parish.getId();
        return null;
    }
}
