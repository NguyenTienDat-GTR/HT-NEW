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
            throw commonValidationHelper.forbidden("Không có quyền tạo vai trò");
        }
    }

    public void requireRoleDisplayOrderManageable(Authentication authentication, Integer displayOrder) {
        if (isSuperAdmin(authentication) || displayOrder == null) {
            return;
        }
        Integer actorOrder = actorRoleOrder(authentication);
        if (actorOrder == null || displayOrder < actorOrder) {
            throw commonValidationHelper.forbidden("Không thể quản lý vai trò có cấp cao hơn phạm vi hiện tại");
        }
    }

    /** SUPER_ADMIN chỉ quản lý vai trò hệ thống hoặc vai trò do super-admin tạo. */
    public boolean isManageableBySuperAdmin(Role role) {
        return role != null && (Boolean.TRUE.equals(role.getIsSystem()) || ManagementOwnership.isSuperAdminOwned(role.getCreatedBy()));
    }

    public void requireManageableBySuperAdmin(Role role) {
        if (!isManageableBySuperAdmin(role)) {
            throw commonValidationHelper.forbidden("SUPER_ADMIN chỉ được quản lý vai trò hệ thống hoặc vai trò do super-admin tạo");
        }
    }

    /** Các cấp giáo phận / giáo hạt / giáo xứ chỉ quản lý vai trò do chính mình (hoặc cấp mình) tạo. */
    private void requireRoleOwnedByActorScope(Authentication authentication, Role role) {
        if (!ManagementOwnership.isOwnedByActorScope(accountRepository, securityUtils.currentUsername(authentication),
                securityUtils.currentAuditActor(authentication), role == null ? null : role.getCreatedBy())) {
            throw commonValidationHelper.forbidden("Chỉ được quản lý vai trò do cấp của mình tạo; vai trò khác chỉ được xem");
        }
    }

    public void requireRoleWriteAccess(Authentication authentication, Role role, String actionCode) {
        if (isSuperAdmin(authentication)) {
            requireManageableBySuperAdmin(role);
            return;
        }
        if (!hasRoleActionAuthority(authentication, actionCode)) {
            throw commonValidationHelper.forbidden("Không có quyền cập nhật vai trò");
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw commonValidationHelper.forbidden("Không được cập nhật vai trò hệ thống");
        }
        requireRoleAtOrBelowActor(authentication, role);
        requireRoleOwnedByActorScope(authentication, role);
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
        requireRolePermissionRoleAccess(authentication, role, authority);
        if (isSuperAdmin(authentication)) {
            return;
        }
        requireActorOwnsPermission(authentication, permission);
        requirePermissionAtOrBelowScope(permission, rolePermissionAuthorityScopeOrder(authentication, authority));
    }

    /**
     * Phần kiểm tra theo vai trò đích (không xét quyền cụ thể) của phân quyền / thu hồi quyền cho vai trò:
     * - SUPER_ADMIN: vai trò hệ thống hoặc do super-admin tạo;
     * - cấp giáo phận được phân quyền cho admin-deanery và admin-parish, cấp giáo hạt cho admin-parish (vai trò hệ thống cấp thấp hơn);
     * - ngoài ra chỉ vai trò tùy biến do chính cấp mình tạo, và không được cao hơn vai trò cao nhất của người thao tác.
     * Vai trò do admin cấp thấp hơn tạo thuộc cấp của họ nên cấp trên không phân quyền được.
     */
    public void requireRolePermissionRoleAccess(Authentication authentication, Role role, String authority) {
        if (isSuperAdmin(authentication)) {
            requireManageableBySuperAdmin(role);
            return;
        }
        requireRolePermissionOperationAuthority(authentication, authority);
        if (isLowerAdminSystemRole(authentication, role, authority)) {
            requireTargetRoleBelowActor(authentication, role);
            return;
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw commonValidationHelper.forbidden("Chỉ SUPER_ADMIN được thay đổi phân quyền của vai trò hệ thống cùng hoặc cao hơn cấp của mình");
        }
        requireRoleOwnedByActorScope(authentication, role);
        requireTargetRoleBelowActor(authentication, role);
    }

    public boolean canManageRolePermissionRole(Authentication authentication, Role role, String authority) {
        try {
            requireRolePermissionRoleAccess(authentication, role, authority);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static final java.util.Map<String, Integer> ADMIN_SYSTEM_ROLE_TIER = java.util.Map.of(
            "ADMIN_DIOCESE", 1,
            "ADMIN_DEANERY", 2,
            "ADMIN_PARISH", 3
    );

    // Vai trò admin hệ thống thuộc cấp thấp hơn cấp thao tác của người dùng (giáo phận > giáo hạt > giáo xứ).
    private boolean isLowerAdminSystemRole(Authentication authentication, Role role, String authority) {
        if (role == null || !Boolean.TRUE.equals(role.getIsSystem())) {
            return false;
        }
        Integer targetTier = ADMIN_SYSTEM_ROLE_TIER.get(role.getRoleCode());
        int actorTier = rolePermissionAuthorityScopeOrder(authentication, authority);
        return targetTier != null && actorTier >= 1 && actorTier != Integer.MAX_VALUE && targetTier > actorTier;
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
            throw commonValidationHelper.forbidden("Không c quyền đọc quyền trong phạm vi quản lí");
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
            requireManageableBySuperAdmin(role);
            return;
        }
        int scopeOrder = rolePermissionReadScopeOrder(authentication);
        if (scopeOrder == Integer.MAX_VALUE) {
            throw commonValidationHelper.forbidden("Không có quyền đo phân quyền vai trò");
        }
        requireRoleAtOrBelowActor(authentication, role);
        requirePermissionAtOrBelowScope(permission, scopeOrder);
    }

    public void requireAccountPermissionWriteAccess(Authentication authentication, String username, Permission permission) {
        if (isSuperAdmin(authentication)) {
            Account target = accountRepository.findById(username)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy tài khoản: " + username));
            if (!ManagementOwnership.isSuperAdminOwned(target.getCreatedBy())) {
                throw commonValidationHelper.forbidden("SUPER_ADMIN chỉ được phân quyền cho tài khoản do super-admin tạo");
            }
            return;
        }
        if (accountManagementAuthorizationHelper != null) {
            Account target = accountRepository.findById(username)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy tài khoản: " + username));
            accountManagementAuthorizationHelper.requireCanManage(authentication, target);
            accountManagementAuthorizationHelper.requireOwnedByActorScope(authentication, target);
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
        throw commonValidationHelper.forbidden("Không có quyền thay đổi phân quyền cho vai trò");
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
            throw commonValidationHelper.forbidden("Chỉ được gán quyền cho vai trò bằng hoặc thấp hơn vai trò hiện tại");
        }
    }

    private void requireRoleAtOrBelowActor(Authentication authentication, Role targetRole) {
        Integer actorOrder = actorRoleOrder(authentication);
        Integer targetOrder = targetRole == null ? null : targetRole.getDisplayOrder();
        if (actorOrder == null || (targetOrder != null && targetOrder < actorOrder)) {
            throw commonValidationHelper.forbidden("Không thểquanrnr l vai trò có cấp cao hơn phạm vi hiện tại");
        }
    }

    private boolean hasRoleActionAuthority(Authentication authentication, String actionCode) {
        return hasAuthority(authentication, "system.role." + actionCode + ".all")
                || hasAuthority(authentication, "system.role." + actionCode + ".diocese")
                || hasAuthority(authentication, "system.role." + actionCode + ".deanery")
                || hasAuthority(authentication, "system.role." + actionCode + ".parish");
    }

    private Integer actorRoleOrder(Authentication authentication) {
        if (accountManagementAuthorizationHelper != null) {
            Integer databaseOrder = accountManagementAuthorizationHelper.currentActorHighestRoleOrder(authentication);
            if (databaseOrder != null) {
                return databaseOrder;
            }
        }
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

    /**
     * Role nào cấp quyền thì chỉ role đó được gỡ, mở rộng theo thứ tự role: người gỡ (không phải SUPER_ADMIN) phải có role
     * cùng cấp hoặc cao hơn role cao nhất của người cấp (displayOrder nhỏ hơn là cấp cao hơn). Ví dụ role A do ADMIN_DIOCESE tạo
     * và cấp quyền cho các role khác thì ADMIN_DIOCESE và các role cùng cấp với A đều gỡ được, role thấp hơn A thì không.
     * Quyền do SUPER_ADMIN cấp có thứ tự cao nhất nên chỉ SUPER_ADMIN gỡ được; quyền do hệ thống cấp hoặc người cấp không còn
     * role hoạt động cũng chỉ SUPER_ADMIN gỡ được.
     */
    public void requireRevocableOrigin(Authentication authentication, String assignedBy, String createdBy) {
        if (isSuperAdmin(authentication)) {
            return;
        }
        String granter = assignedBy != null && !assignedBy.isBlank() ? assignedBy.trim() : createdBy == null ? null : createdBy.trim();
        Integer granterOrder = granter == null || granter.isBlank() || "system".equalsIgnoreCase(granter)
                ? null
                : accountRepository.findHighestActiveRoleOrder(granter);
        Integer actorOrder = actorRoleOrder(authentication);
        if (granterOrder == null || actorOrder == null || actorOrder > granterOrder) {
            throw commonValidationHelper.forbidden("Quyền chỉ được thu hồi bởi role đã cấp quyền, role cùng cấp hoặc cao hơn (hoặc SUPER_ADMIN)");
        }
    }

    /** Admin không thu hồi quyền của chính vai trò mình đang giữ. */
    public void requireNotOwnRole(Authentication authentication, Role role) {
        if (!isSuperAdmin(authentication) && role != null && hasRole(authentication, role.getRoleCode())) {
            throw commonValidationHelper.forbidden("Không được thu hồi quyền của vai trò mà tài khoản hiện tại đang giữ");
        }
    }

    public boolean canRevokeRolePermission(Authentication authentication, Role role, Permission permission, String assignedBy, String createdBy) {
        try {
            requireRolePermissionWriteAccess(authentication, role, permission, ROLE_PERMISSION_REVOKE_DIOCESE);
            requireRevocableOrigin(authentication, assignedBy, createdBy);
            requireNotOwnRole(authentication, role);
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
