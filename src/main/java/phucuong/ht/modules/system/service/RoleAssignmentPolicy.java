package phucuong.ht.modules.system.service;

import org.springframework.security.core.Authentication;
import phucuong.ht.common.security.AuthorityChecks;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Luật gán vai trò cho tài khoản: ai được gán vai trò nào cho tài khoản nào.
 * SUPER_ADMIN có luật riêng; ba cấp quản trị (giáo phận, giáo hạt, giáo xứ) dùng chung một luật, chỉ khác quyền,
 * cách tra phạm vi và thông báo lỗi nên được khai báo trong bảng {@link #LEVELS}.
 */
public class RoleAssignmentPolicy {

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ADMIN_DIOCESE = "ADMIN_DIOCESE";
    private static final String ROLE_ADMIN_DEANERY = "ADMIN_DEANERY";
    private static final String ROLE_ADMIN_PARISH = "ADMIN_PARISH";
    private static final Set<String> SUPER_ADMIN_MANAGED_SYSTEM_ROLES = Set.of(ROLE_SUPER_ADMIN, ROLE_ADMIN_DIOCESE);
    private static final Set<String> SYSTEM_ADMIN_ROLE_CODES = Set.of(ROLE_SUPER_ADMIN, ROLE_ADMIN_DIOCESE, ROLE_ADMIN_DEANERY, ROLE_ADMIN_PARISH);
    private static final Map<String, Set<String>> SCOPED_ADMIN_ASSIGNABLE_SYSTEM_ROLES = Map.of(
            ROLE_ADMIN_DIOCESE, Set.of(ROLE_ADMIN_DEANERY, ROLE_ADMIN_PARISH),
            ROLE_ADMIN_DEANERY, Set.of(ROLE_ADMIN_PARISH),
            ROLE_ADMIN_PARISH, Set.of()
    );

    /** Một cấp quản trị theo phạm vi: quyền gán, vai trò của người gán, cách tra phạm vi và các thông báo lỗi. */
    private record Level(String permission, String actorRoleCode, Function<String, Optional<UUID>> scopeOf,
                         String actorScopeMissing, String targetScopeMissing, String outsideScope) {
    }

    private final AccountManagementAuthorizationHelper authorizationHelper;
    private final AccountRoleRepository accountRoleRepository;
    private final RoleRepository roleRepository;
    private final CommonValidationHelper validation;
    private final List<Level> levels;

    public RoleAssignmentPolicy(AccountRepository accountRepository, AccountRoleRepository accountRoleRepository,
                                RoleRepository roleRepository, CommonValidationHelper validation,
                                AccountManagementAuthorizationHelper authorizationHelper) {
        this.accountRoleRepository = accountRoleRepository;
        this.roleRepository = roleRepository;
        this.validation = validation;
        this.authorizationHelper = authorizationHelper;
        // Thứ tự quan trọng: quyền của cấp cao hơn được xét trước.
        this.levels = List.of(
                new Level("system.account_role.assign.diocese", ROLE_ADMIN_DIOCESE, accountRepository::findActiveDioceseIdByUsername,
                        "Tài khoản hiện tại không có scope giáo phận hợp lệ",
                        "Account đích chưa có scope giáo phận hợp lệ để gán vai trò",
                        "Không thể gán vai trò cho account ngoài giáo phận hiện tại"),
                new Level("system.account_role.assign.deanery", ROLE_ADMIN_DEANERY, accountRepository::findActiveDeaneryIdByUsername,
                        "Tài khoản hiện tại không có phạm vi giáo hạt hợp lệ",
                        "Tài khoản đích chưa có phạm vi giáo hạt hợp lệ để gán vai trò",
                        "Không thể gán vai trò cho tài khoản ngoài giáo hạt hiện tại"),
                new Level("system.account_role.assign.parish", ROLE_ADMIN_PARISH, accountRepository::findActiveParishIdByUsername,
                        "Tài khoản hiện tại không có phạm vi giáo xứ hợp lệ",
                        "Tài khoản đích chưa có phạm vi giáo xứ hợp lệ để gán vai trò",
                        "Không thể gán vai trò cho tài khoản ngoài giáo xứ hiện tại"));
    }

    /** Người dùng hiện tại có thể gán vai trò này cho ai đó (chưa xét tài khoản đích). */
    public boolean canAssign(Authentication authentication, Role role) {
        if (hasRole(authentication, ROLE_SUPER_ADMIN)) {
            return isSuperAdminAssignableRole(role);
        }
        Level level = levelOf(authentication);
        if (level == null) {
            return false;
        }
        String actorRoleCode = level.actorRoleCode();
        if (!canAssignSystemRole(actorRoleCode, role)) {
            return false;
        }
        return roleRepository.findByRoleCodeAndStatusTrue(actorRoleCode)
                .map(actorRole -> {
                    Integer actorOrder = actorHierarchyOrder(authentication, actorRoleCode);
                    Integer targetOrder = role.getDisplayOrder();
                    return actorOrder != null && targetOrder != null && targetOrder >= actorOrder;
                })
                .orElse(false);
    }

    /** Ném lỗi 403 nếu người dùng hiện tại không được gán vai trò này cho tài khoản đích. */
    public void enforce(Authentication authentication, Account targetAccount, Role targetRole) {
        if (currentUsername(authentication).equals(targetAccount.getUsername())) {
            throw validation.forbidden("Quản trị viên không được tự gán vai trò cho tài khoản của mình");
        }

        if (hasRole(authentication, ROLE_SUPER_ADMIN)) {
            enforceSuperAdminScope(targetAccount, targetRole);
            return;
        }

        authorizationHelper.requireTargetAccountNotAboveActor(authentication, targetAccount);

        Level level = levelOf(authentication);
        if (level == null) {
            throw validation.forbidden("Tài khoản hiện tại không có quyền gán vai trò");
        }
        enforceScopedSystemRoleAssignment(level.actorRoleCode(), targetRole);
        UUID actorScope = level.scopeOf().apply(currentUsername(authentication))
                .orElseThrow(() -> validation.forbidden(level.actorScopeMissing()));
        UUID targetScope = level.scopeOf().apply(targetAccount.getUsername())
                .orElseThrow(() -> validation.forbidden(level.targetScopeMissing()));
        if (!actorScope.equals(targetScope)) {
            throw validation.forbidden(level.outsideScope());
        }
        enforceHierarchy(authentication, targetRole, level.actorRoleCode());
    }

    private Level levelOf(Authentication authentication) {
        return levels.stream()
                .filter(level -> AuthorityChecks.hasAuthority(authentication, level.permission()))
                .findFirst()
                .orElse(null);
    }

    private void enforceScopedSystemRoleAssignment(String actorRoleCode, Role targetRole) {
        if (!canAssignSystemRole(actorRoleCode, targetRole)) {
            throw validation.forbidden("Không thể gán vai trò ngoài phạm vi phân cấp");
        }
    }

    private boolean canAssignSystemRole(String actorRoleCode, Role targetRole) {
        if (!SYSTEM_ADMIN_ROLE_CODES.contains(targetRole.getRoleCode()) && !Boolean.TRUE.equals(targetRole.getIsSystem())) {
            return true;
        }
        return SCOPED_ADMIN_ASSIGNABLE_SYSTEM_ROLES.getOrDefault(actorRoleCode, Set.of()).contains(targetRole.getRoleCode());
    }

    private void enforceSuperAdminScope(Account targetAccount, Role targetRole) {
        if (!isSuperAdminAssignableRole(targetRole)) {
            throw validation.forbidden("SUPER_ADMIN chỉ được gán vai trò SUPER_ADMIN, ADMIN_DIOCESE hoặc vai trò custom do super-admin tạo");
        }
        if (!isSuperAdminManagedAccount(targetAccount, targetRole)) {
            throw validation.forbidden("SUPER_ADMIN chỉ được gán vai trò cho tài khaonr SUPER_ADMIN, ADMIN_DIOCESE hoặc tài khoản có vai trò t tạo do super-admin tạo");
        }
        if (ROLE_SUPER_ADMIN.equals(targetRole.getRoleCode()) && !isRootAccount(targetAccount)) {
            throw validation.forbidden("Vai trò SUPER_ADMIN chỉ được gán cho tài khoản không trực thuộc giáo phận/giáo hạt/giáo xứ");
        }
        if (ROLE_ADMIN_DIOCESE.equals(targetRole.getRoleCode()) && targetAccount.getDiocese() == null) {
            throw validation.forbidden("Vai trò ADMIN_DIOCESE chỉ được gán cho tài khoản trực thuộc giáo phận");
        }
    }

    private boolean isSuperAdminManagedAccount(Account targetAccount, Role targetRole) {
        if (ROLE_SUPER_ADMIN.equals(targetRole.getRoleCode()) && isRootAccount(targetAccount)) {
            return true;
        }
        if (ROLE_ADMIN_DIOCESE.equals(targetRole.getRoleCode()) && targetAccount.getDiocese() != null) {
            return true;
        }
        List<String> activeRoleCodes = accountRoleRepository.findActiveRoleCodesByUsername(targetAccount.getUsername());
        return !activeRoleCodes.isEmpty() && activeRoleCodes.stream().allMatch(this::isSuperAdminManagedRoleCode);
    }

    private boolean isSuperAdminManagedRoleCode(String roleCode) {
        if (SUPER_ADMIN_MANAGED_SYSTEM_ROLES.contains(roleCode)) return true;
        return roleRepository.findById(roleCode)
                .map(this::isCustomRoleCreatedBySuperAdmin)
                .orElse(false);
    }

    private boolean isSuperAdminAssignableRole(Role role) {
        return SUPER_ADMIN_MANAGED_SYSTEM_ROLES.contains(role.getRoleCode()) || isCustomRoleCreatedBySuperAdmin(role);
    }

    private boolean isCustomRoleCreatedBySuperAdmin(Role role) {
        return !Boolean.TRUE.equals(role.getIsSystem()) && "super-admin".equalsIgnoreCase(role.getCreatedBy());
    }

    private boolean isRootAccount(Account account) {
        return account.getLeader() == null && account.getDiocese() == null && account.getDeanery() == null && account.getParish() == null;
    }

    private void enforceHierarchy(Authentication authentication, Role targetRole, String actorRoleCode) {
        Integer actorOrder = actorHierarchyOrder(authentication, actorRoleCode);
        Integer targetOrder = targetRole.getDisplayOrder();
        if (actorOrder == null || targetOrder == null || targetOrder < actorOrder) {
            throw validation.forbidden("Không thể gán vai trò cao hơn phạm vi của tài khoản hiện tại");
        }
    }

    private Integer actorHierarchyOrder(Authentication authentication, String fallbackActorRoleCode) {
        Integer actorOrder = authorizationHelper.currentActorHighestRoleOrder(authentication);
        if (actorOrder != null) {
            return actorOrder;
        }
        return roleRepository.findByRoleCodeAndStatusTrue(fallbackActorRoleCode)
                .map(Role::getDisplayOrder)
                .orElseThrow(() -> validation.notFound("Không tìm thấy vai trò " + fallbackActorRoleCode));
    }

    private boolean hasRole(Authentication authentication, String roleCode) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserDetails principal)) {
            return false;
        }
        return principal.getRoles().stream().anyMatch(roleCode::equals);
    }

    private String currentUsername(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserDetails principal)) {
            throw validation.forbidden("Không xác định được tài khoản hiện tại");
        }
        return principal.getUsername();
    }
}
