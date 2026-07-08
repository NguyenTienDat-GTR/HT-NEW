package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.Role;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SystemAdminAuthorizationHelper {

    static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    static final String ROLE_ADMIN_DIOCESE = "ADMIN_DIOCESE";
    static final String ROLE_PERMISSION_ASSIGN_DIOCESE = "system.role_permission.assign.diocese";
    static final String ROLE_PERMISSION_REVOKE_DIOCESE = "system.role_permission.revoke.diocese";
    static final String ACCOUNT_PERMISSION_MANAGE_DIOCESE = "system.account_permission.manage.diocese";

    private final securityUtils securityUtils;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final CommonValidationHelper commonValidationHelper;

    public boolean isSuperAdmin(Authentication authentication) {
        return hasRole(authentication, ROLE_SUPER_ADMIN);
    }

    public boolean hasAuthority(Authentication authentication, String authority) {
        return securityUtils.hasAuthority(authentication, authority);
    }

    public void requireRoleManageableByDioceseAdmin(Role role) {
        Role adminRole = roleRepository.findByRoleCodeAndStatusTrue(ROLE_ADMIN_DIOCESE)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò ADMIN_DIOCESE"));
        Integer actorOrder = adminRole.getDisplayOrder();
        Integer targetOrder = role.getDisplayOrder();
        if (targetOrder == null || actorOrder == null || targetOrder < actorOrder) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không thể quản lý vai trò có cấp cao hơn");
        }
    }

    public void requirePermissionAssignableByDioceseAdmin(Permission permission) {
        String scopeCode = permission.getScope() == null ? null : permission.getScope().getScopeCode();
        if ("all".equalsIgnoreCase(scopeCode)) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không thể gán quyền có phạm vi all");
        }
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
        if (!hasAuthority(authentication, authority)) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền quản lý phân quyền cho vai trò");
        }
        requireRoleManageableByDioceseAdmin(role);
        requirePermissionAssignableByDioceseAdmin(permission);
    }

    public void requireAccountPermissionWriteAccess(Authentication authentication, String username, Permission permission) {
        if (isSuperAdmin(authentication)) {
            return;
        }
        if (!hasAuthority(authentication, ACCOUNT_PERMISSION_MANAGE_DIOCESE)) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền quản lý phân quyền cho tài khoản");
        }
        requireSameDioceseTargetAccount(authentication, username, ACCOUNT_PERMISSION_MANAGE_DIOCESE);
        requirePermissionAssignableByDioceseAdmin(permission);
    }

    private boolean hasRole(Authentication authentication, String roleCode) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + roleCode).equals(authority.getAuthority()));
    }
}
