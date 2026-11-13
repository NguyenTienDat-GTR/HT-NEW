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

import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccountManagementAuthorizationHelper {

    public static final String MANAGE_ALL = "system.account.manage.all";
    public static final String MANAGE_DIOCESE = "system.account.manage.diocese";
    public static final String MANAGE_DEANERY = "system.account.manage.deanery";
    public static final String MANAGE_PARISH = "system.account.manage.parish";
    public static final String RESET_PASSWORD_ALL = "system.account.reset_password.all";
    public static final String RESET_PASSWORD_DIOCESE = "system.account.reset_password.diocese";
    public static final String RESET_PASSWORD_DEANERY = "system.account.reset_password.deanery";
    public static final String RESET_PASSWORD_PARISH = "system.account.reset_password.parish";
    public static final String ACCOUNT_PERMISSION_MANAGE_DIOCESE = "system.account_permission.manage.diocese";
    public static final String ACCOUNT_PERMISSION_MANAGE_DEANERY = "system.account_permission.manage.deanery";
    public static final String ACCOUNT_PERMISSION_MANAGE_PARISH = "system.account_permission.manage.parish";
    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ADMIN_DIOCESE = "ADMIN_DIOCESE";

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final RoleRepository roleRepository;
    private final CommonValidationHelper validation;

    public void requireCanCreate(Authentication authentication, Account target, Role targetRole) {
        if (hasRole(authentication, SUPER_ADMIN)) {
            String targetRoleCode = targetRole.getRoleCode();
            boolean unitless = target.getLeader() == null
                    && target.getDiocese() == null
                    && target.getDeanery() == null
                    && target.getParish() == null;
            boolean creatingSuperAdmin = SUPER_ADMIN.equals(targetRoleCode) && unitless;
            boolean creatingDioceseAdmin = ADMIN_DIOCESE.equals(targetRoleCode) && target.getDiocese() != null;
            boolean creatingSystemWideRole = isSystemWideCustomRole(targetRole) && unitless;
            if (!creatingSuperAdmin && !creatingDioceseAdmin && !creatingSystemWideRole) {
                throw validation.forbidden("SUPER_ADMIN chỉ được tạo tài khoản SUPER_ADMIN, ADMIN_DIOCESE hoặc vai trò toàn hệ thống do super-admin tạo");
            }
            return;
        }

        requireScopedAccess(authentication, target, targetRole);
        Integer actorOrder = currentActorRoleOrder(authentication);
        if (actorOrder == null || order(targetRole) <= actorOrder) {
            throw validation.forbidden("Quản trị viên chỉ được tạo tài khoản có vai trò thấp hơn mình");
        }
    }

    /**
     * Vai trò toàn hệ thống mà SUPER_ADMIN được cấp tài khoản: vai trò tự tạo (không phải vai trò hệ thống như
     * ADMIN_DEANERY / ADMIN_PARISH) và do super-admin tạo, tức không thuộc riêng một giáo phận nào.
     */
    public static boolean isSystemWideCustomRole(Role role) {
        return role != null
                && !Boolean.TRUE.equals(role.getIsSystem())
                && ManagementOwnership.isSuperAdminOwned(role.getCreatedBy());
    }

    public void requireCanManage(Authentication authentication, Account target) {
        if (currentUsername(authentication).equals(target.getUsername())) {
            throw validation.forbidden("Quản trị viên không được tự cập nhật hoặc đổi trạng thái tài khoản của mình");
        }
        if (hasTargetRole(target, SUPER_ADMIN)) {
            throw validation.forbidden("Tài khoản có vai trò SUPER_ADMIN không được cập nhật từ màn hình quản trị tài khoản");
        }
        if (hasRole(authentication, SUPER_ADMIN)) {
            if (!ManagementOwnership.isSuperAdminOwned(target.getCreatedBy())) {
                throw validation.forbidden("SUPER_ADMIN chỉ được quản lý tài khoản do super-admin tạo");
            }
            return;
        }

        Role highestTargetRole = accountRoleRepository.findActiveByUsername(target.getUsername()).stream()
                .map(accountRole -> accountRole.getRole())
                .min((left, right) -> Integer.compare(order(left), order(right)))
                .orElse(null);
        requireScopedAccess(authentication, target, highestTargetRole);
    }

    /**
     * Quyền đặt lại mật khẩu dùng quyền riêng system.account.reset_password.* (không suy ra từ quyền quản lý).
     * Không tự đặt lại mật khẩu của mình, không đặt lại SUPER_ADMIN; các cấp chỉ thao tác trong đúng phạm vi và
     * trên tài khoản có vai trò không cao hơn mình. Việc "do cấp mình tạo" kiểm tra thêm ở requireOwnedByActorScope.
     */
    public void requireCanResetPassword(Authentication authentication, Account target) {
        if (currentUsername(authentication).equals(target.getUsername())) {
            throw validation.forbidden("Quản trị viên không được tự đặt lại mật khẩu của mình, hãy dùng chức năng Quên mật khẩu hoặc Đổi mật khẩu");
        }
        if (hasTargetRole(target, SUPER_ADMIN)) {
            throw validation.forbidden("Không được đặt lại mật khẩu tài khoản có vai trò SUPER_ADMIN");
        }
        if (hasRole(authentication, SUPER_ADMIN)) {
            if (!hasAuthority(authentication, RESET_PASSWORD_ALL)) {
                throw validation.forbidden("Tài khoản hiện tại không có quyền đặt lại mật khẩu tài khoản");
            }
            if (!ManagementOwnership.isSuperAdminOwned(target.getCreatedBy())) {
                throw validation.forbidden("SUPER_ADMIN chỉ được đặt lại mật khẩu tài khoản do super-admin tạo");
            }
            return;
        }

        Role highestTargetRole = accountRoleRepository.findActiveByUsername(target.getUsername()).stream()
                .map(accountRole -> accountRole.getRole())
                .min((left, right) -> Integer.compare(order(left), order(right)))
                .orElse(null);
        if (hasAuthority(authentication, RESET_PASSWORD_DIOCESE)) {
            requireSame("giao phan", actorDiocese(authentication), dioceseId(target));
        } else if (hasAuthority(authentication, RESET_PASSWORD_DEANERY)) {
            requireSame("giao hat", actorDeanery(authentication), deaneryId(target));
        } else if (hasAuthority(authentication, RESET_PASSWORD_PARISH)) {
            requireSame("giao xu", actorParish(authentication), parishId(target));
        } else {
            throw validation.forbidden("Tài khoản hiện tại không có quyền đặt lại mật khẩu tài khoản");
        }
        requireTargetRoleNotAboveActor(authentication, highestTargetRole);
    }

    /** Các cấp giáo phận / giáo hạt / giáo xứ chỉ thay đổi tài khoản do chính mình (hoặc cấp mình) tạo. */
    public void requireOwnedByActorScope(Authentication authentication, Account target) {
        if (hasRole(authentication, SUPER_ADMIN)) {
            return;
        }
        String username = currentUsername(authentication);
        if (!ManagementOwnership.isOwnedByActorScope(accountRepository, authentication, username, username, target.getCreatedBy())
                && !ManagementOwnership.isOwnedByActorScope(accountRepository, authentication, username, auditActor(authentication), target.getCreatedBy())) {
            throw validation.forbidden("Chỉ được thay đổi tài khoản do cấp của mình tạo");
        }
    }

    private String auditActor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUserDetails principal && principal.getLeaderId() != null) {
            return principal.getLeaderId();
        }
        return currentUsername(authentication);
    }

    public void requireTargetAccountNotAboveActor(Authentication authentication, Account target) {
        if (hasRole(authentication, SUPER_ADMIN)) {
            return;
        }
        Role highestTargetRole = accountRoleRepository.findActiveByUsername(target.getUsername()).stream()
                .map(accountRole -> accountRole.getRole())
                .min((left, right) -> Integer.compare(order(left), order(right)))
                .orElse(null);
        requireTargetRoleNotAboveActor(authentication, highestTargetRole);
    }

    public Integer currentActorHighestRoleOrder(Authentication authentication) {
        return currentActorRoleOrder(authentication);
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

    /** Tài khoản đích được phân quyền riêng: quản lý được (không cao hơn người thao tác, không phải chính mình) và do cấp của mình tạo. */
    public boolean canManageForPermissions(Authentication authentication, Account target) {
        if (hasRole(authentication, SUPER_ADMIN)) return true;
        try {
            requireCanManage(authentication, target);
            requireOwnedByActorScope(authentication, target);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public void requireCanRead(Authentication authentication, Account target) {
        if (!canRead(authentication, target)) {
            throw validation.forbidden("Tài khoản hienj tại không có quyền đọc tài khoản này");
        }
    }

    private void requireScopedAccess(Authentication authentication, Account target, Role targetRole) {
        if (hasDioceseManageAuthority(authentication)) {
            requireSame("giao phan", actorDiocese(authentication), dioceseId(target));
            requireTargetRoleNotAboveActor(authentication, targetRole);
            return;
        }
        if (hasDeaneryManageAuthority(authentication)) {
            requireSame("giao hat", actorDeanery(authentication), deaneryId(target));
            requireTargetRoleNotAboveActor(authentication, targetRole);
            return;
        }
        if (hasParishManageAuthority(authentication)) {
            requireSame("giao xu", actorParish(authentication), parishId(target));
            requireTargetRoleNotAboveActor(authentication, targetRole);
            return;
        }
        throw validation.forbidden("Tài khoản hiện tại không có quyền quản lý tài khoản");
    }

    private void requireTargetRoleNotAboveActor(Authentication authentication, Role targetRole) {
        if (targetRole == null) return;
        Integer actorOrder = currentActorRoleOrder(authentication);
        Integer targetOrder = targetRole.getDisplayOrder();
        if (actorOrder == null || targetOrder == null || targetOrder < actorOrder) {
            throw validation.forbidden("Không thể thao tác trên tài khoản có vai trò cao hơn tài khoản hiện tại");
        }
    }

    // Được gọi cho từng dòng khi lọc danh sách (phân quyền vai trò, tài khoản): ghi nhớ trong request đọc để chỉ truy vấn một lần.
    private Integer currentActorRoleOrder(Authentication authentication) {
        String username = currentUsername(authentication);
        String roles = authentication != null && authentication.getPrincipal() instanceof AuthUserDetails principal
                ? String.join(",", principal.getRoles()) : "";
        return phucuong.ht.common.utils.RequestMemo.get("actorRoleOrder:" + username + ":" + roles,
                () -> loadCurrentActorRoleOrder(authentication, username));
    }

    private Integer loadCurrentActorRoleOrder(Authentication authentication, String username) {
        Integer databaseOrder = accountRoleRepository.findActiveByUsername(username).stream()
                .map(accountRole -> accountRole.getRole())
                .map(Role::getDisplayOrder)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);
        if (databaseOrder != null) {
            return databaseOrder;
        }
        if (!(authentication.getPrincipal() instanceof AuthUserDetails principal)) {
            return null;
        }
        return principal.getRoles().stream()
                .map(roleRepository::findByRoleCodeAndStatusTrue)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .map(Role::getDisplayOrder)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);
    }

    private int order(Role role) {
        return role.getDisplayOrder() == null ? Integer.MIN_VALUE : role.getDisplayOrder();
    }

    private void requireSame(String scopeName, UUID actorId, UUID targetId) {
        if (actorId == null || targetId == null || !actorId.equals(targetId)) {
            throw validation.forbidden("Tài khoản đích nằm ngoài " + scopeName + " hiện tại");
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

    private boolean hasDioceseManageAuthority(Authentication auth) {
        return hasAuthority(auth, MANAGE_DIOCESE) || hasAuthority(auth, ACCOUNT_PERMISSION_MANAGE_DIOCESE);
    }

    private boolean hasDeaneryManageAuthority(Authentication auth) {
        return hasAuthority(auth, MANAGE_DEANERY) || hasAuthority(auth, ACCOUNT_PERMISSION_MANAGE_DEANERY);
    }

    private boolean hasParishManageAuthority(Authentication auth) {
        return hasAuthority(auth, MANAGE_PARISH) || hasAuthority(auth, ACCOUNT_PERMISSION_MANAGE_PARISH);
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
            throw validation.forbidden("Không xác định được tài khoản hiện tại");
        }
        return principal.getUsername();
    }
}
