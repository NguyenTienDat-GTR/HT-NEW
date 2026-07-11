package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.Role;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccountManagementAuthorizationHelper {

    public static final String MANAGE_ALL = "system.account.manage.all";
    public static final String MANAGE_DIOCESE = "system.account.manage.diocese";
    public static final String MANAGE_DEANERY = "system.account.manage.deanery";
    public static final String MANAGE_PARISH = "system.account.manage.parish";

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final RoleRepository roleRepository;
    private final CommonValidationHelper validation;

    public void requireCanCreate(Authentication authentication, Account target, Role targetRole) {
        if (hasRole(authentication, "SUPER_ADMIN")) {
            if (!"ADMIN_DIOCESE".equals(targetRole.getRoleCode()) || target.getDiocese() == null) {
                throw validation.forbidden("SUPER_ADMIN chỉ được tạo tài khoản ADMIN_DIOCESE có direct scope giáo phận");
            }
            return;
        }
        requireScopedAccess(authentication, target, targetRole);
        String actorRoleCode = hasAuthority(authentication, MANAGE_DIOCESE) ? "ADMIN_DIOCESE"
                : hasAuthority(authentication, MANAGE_DEANERY) ? "ADMIN_DEANERY" : "ADMIN_PARISH";
        Role actorRole = roleRepository.findByRoleCodeAndStatusTrue(actorRoleCode)
                .orElseThrow(() -> validation.notFound("Không tìm thấy role " + actorRoleCode));
        if (order(targetRole) <= order(actorRole)) {
            throw validation.forbidden("Quản trị viên chỉ được tạo tài khoản có role thấp hơn mình");
        }
    }

    public void requireCanManage(Authentication authentication, Account target) {
        if (currentUsername(authentication).equals(target.getUsername())) {
            throw validation.forbidden("Quản trị viên không được tự cập nhật hoặc đổi trạng thái tài khoản của mình");
        }
        if (hasRole(authentication, "SUPER_ADMIN")) {
            boolean onlyDioceseAdmin = accountRoleRepository.findActiveRoleCodesByUsername(target.getUsername()).stream()
                    .allMatch("ADMIN_DIOCESE"::equals);
            if (!onlyDioceseAdmin || accountRoleRepository.findActiveRoleCodesByUsername(target.getUsername()).isEmpty()
                    || target.getDiocese() == null) {
                throw validation.forbidden("SUPER_ADMIN chỉ được quản lý tài khoản ADMIN_DIOCESE");
            }
            return;
        }
        Role highestTargetRole = accountRoleRepository.findActiveByUsername(target.getUsername()).stream()
                .map(accountRole -> accountRole.getRole())
                .min((left, right) -> Integer.compare(order(left), order(right)))
                .orElse(null);
        requireScopedAccess(authentication, target, highestTargetRole);
    }

    public boolean canRead(Authentication authentication, Account target) {
        if (hasRole(authentication, "SUPER_ADMIN")) return false;
        try {
            requireCanManage(authentication, target);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public void requireCanRead(Authentication authentication, Account target) {
        if (!canRead(authentication, target)) {
            throw validation.forbidden("Tài khoản hiện tại không có quyền đọc account này");
        }
    }

    public void requireCanDelete(Authentication authentication, Account target) {
        if (hasRole(authentication, "SUPER_ADMIN")) {
            throw validation.forbidden("SUPER_ADMIN chỉ có quyền tạo, cập nhật và đổi trạng thái account ADMIN_DIOCESE");
        }
        requireCanManage(authentication, target);
    }

    private void requireScopedAccess(Authentication authentication, Account target, Role targetRole) {
        if (hasAuthority(authentication, MANAGE_DIOCESE)) {
            requireSame("giáo phận", actorDiocese(authentication), dioceseId(target));
            requireHierarchy(targetRole, "ADMIN_DIOCESE");
            return;
        }
        if (hasAuthority(authentication, MANAGE_DEANERY)) {
            requireSame("giáo hạt", actorDeanery(authentication), deaneryId(target));
            requireHierarchy(targetRole, "ADMIN_DEANERY");
            return;
        }
        if (hasAuthority(authentication, MANAGE_PARISH)) {
            requireSame("giáo xứ", actorParish(authentication), parishId(target));
            requireHierarchy(targetRole, "ADMIN_PARISH");
            return;
        }
        throw validation.forbidden("Tài khoản hiện tại không có quyền quản lý tài khoản");
    }

    private void requireHierarchy(Role targetRole, String actorRoleCode) {
        if (targetRole == null) return;
        Role actorRole = roleRepository.findByRoleCodeAndStatusTrue(actorRoleCode)
                .orElseThrow(() -> validation.notFound("Không tìm thấy role " + actorRoleCode));
        if (order(targetRole) < order(actorRole)) {
            throw validation.forbidden("Không thể quản lý tài khoản có role cao hơn phạm vi hiện tại");
        }
    }

    private int order(Role role) { return role.getDisplayOrder() == null ? Integer.MIN_VALUE : role.getDisplayOrder(); }
    private void requireSame(String scopeName, UUID actorId, UUID targetId) {
        if (actorId == null || targetId == null || !actorId.equals(targetId)) {
            throw validation.forbidden("Tài khoản đích nằm ngoài " + scopeName + " hiện tại");
        }
    }
    private UUID actorDiocese(Authentication auth) { return accountRepository.findActiveDioceseIdByUsername(currentUsername(auth)).orElse(null); }
    private UUID actorDeanery(Authentication auth) { return accountRepository.findActiveDeaneryIdByUsername(currentUsername(auth)).orElse(null); }
    private UUID actorParish(Authentication auth) { return accountRepository.findActiveParishIdByUsername(currentUsername(auth)).orElse(null); }
    private UUID dioceseId(Account a) {
        if (a.getDiocese() != null) return a.getDiocese().getId();
        if (a.getDeanery() != null) return a.getDeanery().getDiocese().getId();
        if (a.getParish() != null) return a.getParish().getDeanery().getDiocese().getId();
        return a.getLeader() == null ? null : a.getLeader().getParish().getDeanery().getDiocese().getId();
    }
    private UUID deaneryId(Account a) {
        if (a.getDeanery() != null) return a.getDeanery().getId();
        if (a.getParish() != null) return a.getParish().getDeanery().getId();
        return a.getLeader() == null ? null : a.getLeader().getParish().getDeanery().getId();
    }
    private UUID parishId(Account a) {
        if (a.getParish() != null) return a.getParish().getId();
        return a.getLeader() == null ? null : a.getLeader().getParish().getId();
    }
    private boolean hasAuthority(Authentication auth, String permission) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> permission.equals(a.getAuthority()));
    }
    private boolean hasRole(Authentication auth, String roleCode) {
        return auth != null && auth.getPrincipal() instanceof AuthUserDetails principal
                && principal.getRoles().contains(roleCode);
    }
    private String currentUsername(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AuthUserDetails principal)) {
            throw validation.forbidden("Không xác định được tài khoản hiện tại");
        }
        return principal.getUsername();
    }
}
