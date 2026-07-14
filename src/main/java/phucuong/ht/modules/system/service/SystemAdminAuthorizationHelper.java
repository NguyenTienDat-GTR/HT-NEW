package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.Role;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor(onConstructor_ = @org.springframework.beans.factory.annotation.Autowired)
public class SystemAdminAuthorizationHelper {
    static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    static final String ROLE_ADMIN_DIOCESE = "ADMIN_DIOCESE";
    static final String ROLE_PERMISSION_ASSIGN_ALL = "system.role_permission.assign.all";
    static final String ROLE_PERMISSION_ASSIGN_DIOCESE = "system.role_permission.assign.diocese";
    static final String ROLE_PERMISSION_REVOKE_ALL = "system.role_permission.revoke.all";
    static final String ROLE_PERMISSION_REVOKE_DIOCESE = "system.role_permission.revoke.diocese";
    static final String ACCOUNT_PERMISSION_MANAGE_DIOCESE = "system.account_permission.manage.diocese";
    static final String ACCOUNT_PERMISSION_MANAGE_DEANERY = "system.account_permission.manage.deanery";
    static final String ACCOUNT_PERMISSION_MANAGE_PARISH = "system.account_permission.manage.parish";

    private final securityUtils securityUtils;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final AccountManagementAuthorizationHelper accountManagementAuthorizationHelper;

    SystemAdminAuthorizationHelper(
            securityUtils securityUtils,
            AccountScopeValidationHelper accountScopeValidationHelper,
            AccountRepository accountRepository,
            RoleRepository roleRepository,
            CommonValidationHelper commonValidationHelper
    ) {
        this(securityUtils, accountScopeValidationHelper, accountRepository, roleRepository, commonValidationHelper, null);
    }

    public boolean isSuperAdmin(Authentication authentication) {
        return hasRole(authentication, ROLE_SUPER_ADMIN);
    }

    public boolean hasAuthority(Authentication authentication, String authority) {
        return securityUtils.hasAuthority(authentication, authority);
    }

    public void requireRoleManageableByDioceseAdmin(Role role) {
        Role adminRole = roleRepository.findByRoleCodeAndStatusTrue(ROLE_ADMIN_DIOCESE)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò ADMIN_DIOCESE"));
        if (role.getDisplayOrder() == null || adminRole.getDisplayOrder() == null || role.getDisplayOrder() < adminRole.getDisplayOrder()) {
            throw commonValidationHelper.forbidden("Không thể quản lý vai trò có cấp cao hơn");
        }
    }

    public void requirePermissionAssignableByDioceseAdmin(Permission permission) {
        requirePermissionAtOrBelowScope(permission, 1);
    }

    public void requireSameDioceseTargetAccount(Authentication authentication, String username, String permissionCode) {
        UUID actorDioceseId = accountScopeValidationHelper.requireActiveDioceseId(authentication, permissionCode);
        UUID targetDioceseId = accountRepository.findActiveDioceseIdByUsername(username)
                .orElseThrow(() -> commonValidationHelper.forbidden("Tài khoản đích không có phạm vi giáo phận hợp lệ"));
        if (!actorDioceseId.equals(targetDioceseId)) {
            throw commonValidationHelper.forbidden("Tài khoản đích nằm ngoài giáo phận hiện tại");
        }
    }

    public void requireRolePermissionWriteAccess(Authentication authentication, Role role, Permission permission, String authority) {
        if (isSuperAdmin(authentication)) {
            return;
        }
        requireRolePermissionOperationAuthority(authentication, authority);
        requireActorOwnsPermission(authentication, permission);
        requireTargetRoleBelowActor(authentication, role);
        requirePermissionAtOrBelowScope(permission, rolePermissionAuthorityScopeOrder(authentication, authority));
    }

    public boolean canReadRole(Authentication authentication, Role role) {
        try {
            requireRoleReadAccess(authentication, role);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public void requireRoleReadAccess(Authentication authentication, Role role) {
        if (isSuperAdmin(authentication)) {
            return;
        }
        if (!hasAuthority(authentication, "system.role.read.all")) {
            throw commonValidationHelper.forbidden("Không có quyền đọc vai trò");
        }
        requireRoleManageableByDioceseAdmin(role);
    }

    public boolean canReadPermission(Authentication authentication, Permission permission) {
        try {
            requirePermissionReadAccess(authentication, permission);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public void requirePermissionReadAccess(Authentication authentication, Permission permission) {
        if (isSuperAdmin(authentication)) {
            return;
        }
        if (!hasAuthority(authentication, ROLE_PERMISSION_ASSIGN_DIOCESE)
                && !hasAuthority(authentication, ROLE_PERMISSION_REVOKE_DIOCESE)) {
            throw commonValidationHelper.forbidden("Không có quyền đọc permission trong phạm vi quản lý");
        }
        requirePermissionAssignableByDioceseAdmin(permission);
    }

    public boolean canReadRolePermission(Authentication authentication, Role role, Permission permission) {
        try {
            requireRolePermissionReadAccess(authentication, role, permission);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public void requireRolePermissionReadAccess(Authentication authentication, Role role, Permission permission) {
        if (isSuperAdmin(authentication)) {
            return;
        }
        if (!hasAuthority(authentication, ROLE_PERMISSION_ASSIGN_DIOCESE)
                && !hasAuthority(authentication, ROLE_PERMISSION_REVOKE_DIOCESE)) {
            throw commonValidationHelper.forbidden("Không có quyền đọc phân quyền vai trò");
        }
        requireRoleManageableByDioceseAdmin(role);
        requirePermissionAssignableByDioceseAdmin(permission);
    }

    public void requireAccountPermissionWriteAccess(Authentication authentication, String username, Permission permission) {
        if (isSuperAdmin(authentication)) return;
        if (accountManagementAuthorizationHelper != null) {
            Account target = accountRepository.findById(username)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy tài khoản: " + username));
            accountManagementAuthorizationHelper.requireCanManage(authentication, target);
        }
        if (!hasAuthority(authentication, permission.getPermissionCode())) {
            throw commonValidationHelper.forbidden("Không thể gán quyền mà tài khoản hiện tại không sở hữu");
        }
        if (hasAuthority(authentication, ACCOUNT_PERMISSION_MANAGE_DIOCESE)) {
            requirePermissionAtOrBelowScope(permission, 1);
            return;
        }
        if (hasAuthority(authentication, ACCOUNT_PERMISSION_MANAGE_DEANERY)) {
            requirePermissionAtOrBelowScope(permission, 2);
            return;
        }
        if (hasAuthority(authentication, ACCOUNT_PERMISSION_MANAGE_PARISH)) {
            requirePermissionAtOrBelowScope(permission, 3);
            return;
        }
        throw commonValidationHelper.forbidden("Không có quyền quản lý phân quyền cho tài khoản");
    }

    private void requirePermissionAtOrBelowScope(Permission permission, int actorScopeOrder) {
        String code = permission.getScope() == null ? "" : permission.getScope().getScopeCode();
        int permissionScopeOrder = switch (code == null ? "" : code.toLowerCase()) {
            case "all" -> 0;
            case "diocese" -> 1;
            case "deanery" -> 2;
            case "parish", "own", "none" -> 3;
            default -> 0;
        };
        if (permissionScopeOrder < actorScopeOrder) {
            throw commonValidationHelper.forbidden("Không thể gán quyền có phạm vi cao hơn phạm vi quản lý hiện tại");
        }
    }

    private void requireRolePermissionOperationAuthority(Authentication authentication, String authority) {
        if (hasAuthority(authentication, authority) || hasAuthority(authentication, matchingAllScopeAuthority(authority))) {
            return;
        }
        throw commonValidationHelper.forbidden("Không có quyền thay đổi phân quyền cho vai trò");
    }

    private String matchingAllScopeAuthority(String authority) {
        if (ROLE_PERMISSION_REVOKE_DIOCESE.equals(authority)) {
            return ROLE_PERMISSION_REVOKE_ALL;
        }
        return ROLE_PERMISSION_ASSIGN_ALL;
    }

    private int rolePermissionAuthorityScopeOrder(Authentication authentication, String authority) {
        if (hasAuthority(authentication, matchingAllScopeAuthority(authority))) {
            return 0;
        }
        return 1;
    }

    private void requireActorOwnsPermission(Authentication authentication, Permission permission) {
        String permissionCode = permission == null ? null : permission.getPermissionCode();
        if (permissionCode == null || permissionCode.isBlank() || !hasAuthority(authentication, permissionCode)) {
            throw commonValidationHelper.forbidden("Không thể gán quyền mà tài khoản hiện tại không sở hữu");
        }
    }

    private void requireTargetRoleBelowActor(Authentication authentication, Role targetRole) {
        Integer targetOrder = targetRole == null ? null : targetRole.getDisplayOrder();
        Integer actorOrder = actorRoleCodes(authentication).stream()
                .map(roleRepository::findByRoleCodeAndStatusTrue)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Role::getDisplayOrder)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);
        if (actorOrder == null || targetOrder == null || targetOrder <= actorOrder) {
            throw commonValidationHelper.forbidden("Chỉ được gán quyền cho vai trò thấp hơn vai trò hiện tại");
        }
    }

    private List<String> actorRoleCodes(Authentication authentication) {
        if (authentication == null) {
            return List.of();
        }
        return authentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .filter(authority -> authority != null && authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .filter(roleCode -> !roleCode.isBlank())
                .toList();
    }

    public boolean canManageAccountPermission(Authentication authentication, String username, Permission permission) {
        try {
            requireAccountPermissionWriteAccess(authentication, username, permission);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean hasRole(Authentication authentication, String roleCode) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + roleCode).equals(authority.getAuthority()));
    }
}
