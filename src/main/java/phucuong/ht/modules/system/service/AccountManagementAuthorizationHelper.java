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
    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ADMIN_DIOCESE = "ADMIN_DIOCESE";

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final RoleRepository roleRepository;
    private final CommonValidationHelper validation;

    public void requireCanCreate(Authentication authentication, Account target, Role targetRole) {
        if (hasRole(authentication, SUPER_ADMIN)) {
            String targetRoleCode = targetRole.getRoleCode();
            boolean creatingSuperAdmin = SUPER_ADMIN.equals(targetRoleCode)
                    && target.getLeader() == null
                    && target.getDiocese() == null
                    && target.getDeanery() == null
                    && target.getParish() == null;
            boolean creatingDioceseAdmin = ADMIN_DIOCESE.equals(targetRoleCode) && target.getDiocese() != null;
            if (!creatingSuperAdmin && !creatingDioceseAdmin) {
                throw validation.forbidden("SUPER_ADMIN chi duoc tao tai khoan SUPER_ADMIN hoac ADMIN_DIOCESE hop le");
            }
            return;
        }

        requireScopedAccess(authentication, target, targetRole);
        String actorRoleCode = hasAuthority(authentication, MANAGE_DIOCESE) ? "ADMIN_DIOCESE"
                : hasAuthority(authentication, MANAGE_DEANERY) ? "ADMIN_DEANERY" : "ADMIN_PARISH";
        Role actorRole = roleRepository.findByRoleCodeAndStatusTrue(actorRoleCode)
                .orElseThrow(() -> validation.notFound("Khong tim thay role " + actorRoleCode));
        if (order(targetRole) <= order(actorRole)) {
            throw validation.forbidden("Quan tri vien chi duoc tao tai khoan co role thap hon minh");
        }
    }

    public void requireCanManage(Authentication authentication, Account target) {
        if (currentUsername(authentication).equals(target.getUsername())) {
            throw validation.forbidden("Quan tri vien khong duoc tu cap nhat hoac doi trang thai tai khoan cua minh");
        }
        if (hasTargetRole(target, SUPER_ADMIN)) {
            throw validation.forbidden("Tai khoan co role SUPER_ADMIN khong duoc cap nhat tu man hinh quan tri account");
        }
        if (hasRole(authentication, SUPER_ADMIN)) {
            boolean onlyDioceseAdmin = accountRoleRepository.findActiveRoleCodesByUsername(target.getUsername()).stream()
                    .allMatch(ADMIN_DIOCESE::equals);
            if (!onlyDioceseAdmin || accountRoleRepository.findActiveRoleCodesByUsername(target.getUsername()).isEmpty()
                    || target.getDiocese() == null) {
                throw validation.forbidden("SUPER_ADMIN chi duoc quan ly tai khoan ADMIN_DIOCESE");
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
        if (hasRole(authentication, SUPER_ADMIN)) return true;
        try {
            requireCanManage(authentication, target);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public void requireCanRead(Authentication authentication, Account target) {
        if (!canRead(authentication, target)) {
            throw validation.forbidden("Tai khoan hien tai khong co quyen doc account nay");
        }
    }

    private void requireScopedAccess(Authentication authentication, Account target, Role targetRole) {
        if (hasAuthority(authentication, MANAGE_DIOCESE)) {
            requireSame("giao phan", actorDiocese(authentication), dioceseId(target));
            requireHierarchy(targetRole, "ADMIN_DIOCESE");
            return;
        }
        if (hasAuthority(authentication, MANAGE_DEANERY)) {
            requireSame("giao hat", actorDeanery(authentication), deaneryId(target));
            requireHierarchy(targetRole, "ADMIN_DEANERY");
            return;
        }
        if (hasAuthority(authentication, MANAGE_PARISH)) {
            requireSame("giao xu", actorParish(authentication), parishId(target));
            requireHierarchy(targetRole, "ADMIN_PARISH");
            return;
        }
        throw validation.forbidden("Tai khoan hien tai khong co quyen quan ly tai khoan");
    }

    private void requireHierarchy(Role targetRole, String actorRoleCode) {
        if (targetRole == null) return;
        Role actorRole = roleRepository.findByRoleCodeAndStatusTrue(actorRoleCode)
                .orElseThrow(() -> validation.notFound("Khong tim thay role " + actorRoleCode));
        if (order(targetRole) < order(actorRole)) {
            throw validation.forbidden("Khong the quan ly tai khoan co role cao hon pham vi hien tai");
        }
    }

    private int order(Role role) {
        return role.getDisplayOrder() == null ? Integer.MIN_VALUE : role.getDisplayOrder();
    }

    private void requireSame(String scopeName, UUID actorId, UUID targetId) {
        if (actorId == null || targetId == null || !actorId.equals(targetId)) {
            throw validation.forbidden("Tai khoan dich nam ngoai " + scopeName + " hien tai");
        }
    }

    private UUID actorDiocese(Authentication auth) {
        return accountRepository.findActiveDioceseIdByUsername(currentUsername(auth)).orElse(null);
    }

    private UUID actorDeanery(Authentication auth) {
        return accountRepository.findActiveDeaneryIdByUsername(currentUsername(auth)).orElse(null);
    }

    private UUID actorParish(Authentication auth) {
        return accountRepository.findActiveParishIdByUsername(currentUsername(auth)).orElse(null);
    }

    private UUID dioceseId(Account account) {
        if (account.getDiocese() != null) return account.getDiocese().getId();
        if (account.getDeanery() != null) return account.getDeanery().getDiocese().getId();
        if (account.getParish() != null) return account.getParish().getDeanery().getDiocese().getId();
        return account.getLeader() == null ? null : account.getLeader().getParish().getDeanery().getDiocese().getId();
    }

    private UUID deaneryId(Account account) {
        if (account.getDeanery() != null) return account.getDeanery().getId();
        if (account.getParish() != null) return account.getParish().getDeanery().getId();
        return account.getLeader() == null ? null : account.getLeader().getParish().getDeanery().getId();
    }

    private UUID parishId(Account account) {
        if (account.getParish() != null) return account.getParish().getId();
        return account.getLeader() == null ? null : account.getLeader().getParish().getId();
    }

    private boolean hasAuthority(Authentication auth, String permission) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> permission.equals(a.getAuthority()));
    }

    private boolean hasRole(Authentication auth, String roleCode) {
        return auth != null && auth.getPrincipal() instanceof AuthUserDetails principal
                && principal.getRoles().contains(roleCode);
    }

    private boolean hasTargetRole(Account target, String roleCode) {
        return target != null && accountRoleRepository.findActiveRoleCodesByUsername(target.getUsername()).contains(roleCode);
    }

    private String currentUsername(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AuthUserDetails principal)) {
            throw validation.forbidden("Khong xac dinh duoc tai khoan hien tai");
        }
        return principal.getUsername();
    }
}
