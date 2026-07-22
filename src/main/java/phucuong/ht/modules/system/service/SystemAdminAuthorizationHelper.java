package phucuong.ht.modules.system.service;

import org.springframework.beans.factory.annotation.Autowired;
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
public class SystemAdminAuthorizationHelper {
    static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    static final String ROLE_ADMIN_DIOCESE = "ADMIN_DIOCESE";
    static final String ROLE_PERMISSION_ASSIGN_ALL = "system.role_permission.assign.all";
    static final String ROLE_PERMISSION_ASSIGN_DIOCESE = "system.role_permission.assign.diocese";
    static final String ROLE_PERMISSION_ASSIGN_DEANERY = "system.role_permission.assign.deanery";
    static final String ROLE_PERMISSION_ASSIGN_PARISH = "system.role_permission.assign.parish";
    static final String ROLE_PERMISSION_REVOKE_ALL = "system.role_permission.revoke.all";
    static final String ROLE_PERMISSION_REVOKE_DIOCESE = "system.role_permission.revoke.diocese";
    static final String ROLE_PERMISSION_REVOKE_DEANERY = "system.role_permission.revoke.deanery";
    static final String ROLE_PERMISSION_REVOKE_PARISH = "system.role_permission.revoke.parish";
    static final String ROLE_PERMISSION_READ_ALL = "system.role_permission.read.all";
    static final String ROLE_PERMISSION_READ_DIOCESE = "system.role_permission.read.diocese";
    static final String ROLE_PERMISSION_READ_DEANERY = "system.role_permission.read.deanery";
    static final String ROLE_PERMISSION_READ_PARISH = "system.role_permission.read.parish";
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

    @Autowired
    public SystemAdminAuthorizationHelper(
            securityUtils securityUtils,
            AccountScopeValidationHelper accountScopeValidationHelper,
            AccountRepository accountRepository,
            RoleRepository roleRepository,
            CommonValidationHelper commonValidationHelper,
            AccountManagementAuthorizationHelper accountManagementAuthorizationHelper
    ) {
        this.securityUtils = securityUtils;
        this.accountScopeValidationHelper = accountScopeValidationHelper;
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.commonValidationHelper = commonValidationHelper;
        this.accountManagementAuthorizationHelper = accountManagementAuthorizationHelper;
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

    public void requireRoleCreateAccess(Authentication authentication) {
        if (isSuperAdmin(authentication)) {
            return;
        }
        if (!hasRoleActionAuthority(authentication, "create")) {
            throw commonValidationHelper.forbidden("Khong co quyen tao vai tro");
        }
    }

    public void requireRoleDisplayOrderManageable(Authentication authentication, Integer displayOrder) {
        if (isSuperAdmin(authentication) || displayOrder == null) {
            return;
        }
        Integer actorOrder = actorRoleOrder(authentication);
        if (actorOrder == null || displayOrder < actorOrder) {
            throw commonValidationHelper.forbidden("Khong the quan ly vai tro co cap cao hon pham vi hien tai");
        }
    }

    public void requireRoleWriteAccess(Authentication authentication, Role role, String actionCode) {
        if (isSuperAdmin(authentication)) {
            return;
        }
        if (!hasRoleActionAuthority(authentication, actionCode)) {
            throw commonValidationHelper.forbidden("Khong co quyen cap nhat vai tro");
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw commonValidationHelper.forbidden("Khong duoc cap nhat vai tro he thong");
        }
        requireRoleAtOrBelowActor(authentication, role);
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
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw commonValidationHelper.forbidden("Chi SUPER_ADMIN duoc thay doi phan quyen cua vai tro he thong");
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
        if (!hasRoleActionAuthority(authentication, "read")) {
            throw commonValidationHelper.forbidden("Không có quyền đọc vai trò");
        }
        requireRoleAtOrBelowActor(authentication, role);
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
        int scopeOrder = rolePermissionReadScopeOrder(authentication);
        if (scopeOrder == Integer.MAX_VALUE) {
            throw commonValidationHelper.forbidden("Khong co quyen doc permission trong pham vi quan ly");
        }
        requirePermissionAtOrBelowScope(permission, scopeOrder);
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
        int scopeOrder = rolePermissionReadScopeOrder(authentication);
        if (scopeOrder == Integer.MAX_VALUE) {
            throw commonValidationHelper.forbidden("Khong co quyen doc phan quyen vai tro");
        }
        requireRoleAtOrBelowActor(authentication, role);
        requirePermissionAtOrBelowScope(permission, scopeOrder);
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
        if (rolePermissionAuthorityScopeOrder(authentication, authority) != Integer.MAX_VALUE) {
            return;
        }
        throw commonValidationHelper.forbidden("Khong co quyen thay doi phan quyen cho vai tro");
    }

    private int rolePermissionReadScopeOrder(Authentication authentication) {
        return List.of(
                        rolePermissionScopeOrder(authentication, "system.role_permission.read."),
                        rolePermissionScopeOrder(authentication, "system.role_permission.assign."),
                        rolePermissionScopeOrder(authentication, "system.role_permission.revoke.")
                ).stream()
                .min(Integer::compareTo)
                .orElse(Integer.MAX_VALUE);
    }

    private int rolePermissionAuthorityScopeOrder(Authentication authentication, String authority) {
        String prefix = authority != null && authority.contains(".revoke.")
                ? "system.role_permission.revoke."
                : "system.role_permission.assign.";
        return rolePermissionScopeOrder(authentication, prefix);
    }

    private int rolePermissionScopeOrder(Authentication authentication, String prefix) {
        if (hasAuthority(authentication, prefix + "all")) {
            return 0;
        }
        if (hasAuthority(authentication, prefix + "diocese")) {
            return 1;
        }
        if (hasAuthority(authentication, prefix + "deanery")) {
            return 2;
        }
        if (hasAuthority(authentication, prefix + "parish")) {
            return 3;
        }
        return Integer.MAX_VALUE;
    }

    private void requireActorOwnsPermission(Authentication authentication, Permission permission) {
        String permissionCode = permission == null ? null : permission.getPermissionCode();
        if (permissionCode == null || permissionCode.isBlank() || !hasAuthority(authentication, permissionCode)) {
            throw commonValidationHelper.forbidden("Không thể gán quyền mà tài khoản hiện tại không sở hữu");
        }
    }

    private void requireTargetRoleBelowActor(Authentication authentication, Role targetRole) {
        Integer targetOrder = targetRole == null ? null : targetRole.getDisplayOrder();
        Integer actorOrder = actorRoleOrder(authentication);
        if (actorOrder == null || targetOrder == null || targetOrder < actorOrder) {
            throw commonValidationHelper.forbidden("Chi duoc gan quyen cho vai tro bang hoac thap hon vai tro hien tai");
        }
    }

    private void requireRoleAtOrBelowActor(Authentication authentication, Role targetRole) {
        Integer actorOrder = actorRoleOrder(authentication);
        Integer targetOrder = targetRole == null ? null : targetRole.getDisplayOrder();
        if (actorOrder == null || (targetOrder != null && targetOrder < actorOrder)) {
            throw commonValidationHelper.forbidden("Khong the quan ly vai tro co cap cao hon pham vi hien tai");
        }
    }

    private boolean hasRoleActionAuthority(Authentication authentication, String actionCode) {
        return hasAuthority(authentication, "system.role." + actionCode + ".all")
                || hasAuthority(authentication, "system.role." + actionCode + ".diocese")
                || hasAuthority(authentication, "system.role." + actionCode + ".deanery")
                || hasAuthority(authentication, "system.role." + actionCode + ".parish");
    }

    private Integer actorRoleOrder(Authentication authentication) {
        return actorRoleCodes(authentication).stream()
                .map(roleRepository::findByRoleCodeAndStatusTrue)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Role::getDisplayOrder)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);
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
