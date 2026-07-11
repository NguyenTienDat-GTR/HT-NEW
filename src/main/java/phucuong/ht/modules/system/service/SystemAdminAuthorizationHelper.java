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

import java.util.UUID;

@Component
@RequiredArgsConstructor(onConstructor_ = @org.springframework.beans.factory.annotation.Autowired)
public class SystemAdminAuthorizationHelper {
    static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    static final String ROLE_ADMIN_DIOCESE = "ADMIN_DIOCESE";
    static final String ROLE_PERMISSION_ASSIGN_DIOCESE = "system.role_permission.assign.diocese";
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

    public boolean isSuperAdmin(Authentication authentication) { return hasRole(authentication, ROLE_SUPER_ADMIN); }
    public boolean hasAuthority(Authentication authentication, String authority) { return securityUtils.hasAuthority(authentication, authority); }

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
        if (!actorDioceseId.equals(targetDioceseId)) throw commonValidationHelper.forbidden("Tài khoản đích nằm ngoài giáo phận hiện tại");
    }

    public void requireRolePermissionWriteAccess(Authentication authentication, Role role, Permission permission, String authority) {
        if (isSuperAdmin(authentication)) return;
        if (!hasAuthority(authentication, authority)) throw commonValidationHelper.forbidden("Không có quyền quản lý phân quyền cho vai trò");
        requireRoleManageableByDioceseAdmin(role);
        requirePermissionAssignableByDioceseAdmin(permission);
    }

    public void requireAccountPermissionWriteAccess(Authentication authentication, String username, Permission permission) {
        if (accountManagementAuthorizationHelper != null) {
            Account target = accountRepository.findById(username)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy tài khoản: " + username));
            accountManagementAuthorizationHelper.requireCanManage(authentication, target);
        }
        if (isSuperAdmin(authentication)) return;
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
        if (permissionScopeOrder < actorScopeOrder) throw commonValidationHelper.forbidden("Không thể gán quyền có phạm vi cao hơn phạm vi quản lý hiện tại");
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
