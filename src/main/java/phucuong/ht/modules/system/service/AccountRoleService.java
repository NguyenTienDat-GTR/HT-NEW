package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import phucuong.ht.common.repository.InMemoryPager;
import phucuong.ht.common.security.AuthorityChecks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.security.UnitOperationalStatusService;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.notification.service.ToggleNotificationDispatcher;
import phucuong.ht.modules.notification.service.ToggleNotificationRecipientResolver;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountRole;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.request.AccountRoleBulkRequest;
import phucuong.ht.modules.system.model.request.AccountRoleCreateRequest;
import phucuong.ht.modules.system.model.request.AccountRoleUpdateRequest;
import phucuong.ht.modules.system.model.response.AccountRoleResponse;
import phucuong.ht.modules.system.model.response.RoleResponse;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AccountRoleService {

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

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final RoleRepository roleRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final AccountManagementAuthorizationHelper accountManagementAuthorizationHelper;
    private final phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;
    @Autowired(required = false)
    private UnitOperationalStatusService unitOperationalStatusService;
    @Autowired(required = false)
    private ToggleNotificationRecipientResolver toggleNotificationRecipientResolver;
    @Autowired(required = false)
    private ToggleNotificationDispatcher toggleNotificationDispatcher;
    @Autowired(required = false)
    private RbacAuthRefreshNotifier authRefreshNotifier;

    @Transactional
    public AccountRoleResponse assignRole(AccountRoleCreateRequest request, Authentication authentication) {
        Account account = requireAccount(request.getUsername());
        Role targetRole = requireActiveRole(request.getRoleCode());

        enforceAssignmentScope(authentication, account, targetRole);

        OffsetDateTime now = OffsetDateTime.now();
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người gán vai trò");

        AccountRole accountRole = accountRoleRepository.findByAccountUsernameAndRoleRoleCode(account.getUsername(), targetRole.getRoleCode())
                .orElseGet(AccountRole::new);
        boolean primary = Boolean.TRUE.equals(request.getIsPrimary());
        if (primary) {
            clearOtherPrimaryRolesAndFlush(account.getUsername(), targetRole.getRoleCode(), actor, now);
        }
        applyAssignment(accountRole, account, targetRole, primary, request.getAssignedAt(), request.getExpiresAt(), actor, now);
        AccountRole saved = accountRoleRepository.save(accountRole);
        notifyAccountRoleChanged(account.getUsername(), "ACCOUNT_ROLE_ASSIGNED");
        return toResponse(saved);
    }

    @Transactional
    public List<AccountRoleResponse> bulkAssign(AccountRoleBulkRequest request, Authentication authentication) {
        Account account = requireAccount(request.getUsername());
        List<String> roleCodes = new LinkedHashSet<>(request.getRoleCodes()).stream().toList();
        if (roleCodes.isEmpty()) {
            throw commonValidationHelper.validationError("Vui lòng chọn ít nhất một vai trò");
        }
        String primaryRoleCode = commonValidationHelper.normalizeOptionalText(request.getPrimaryRoleCode());
        if (primaryRoleCode != null && !roleCodes.contains(primaryRoleCode)) {
            throw commonValidationHelper.validationError("Vai trò chính phải nằm trong danh sách vai trò đã chọn");
        }

        OffsetDateTime now = OffsetDateTime.now();
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người gán vai trò");

        if (primaryRoleCode != null) {
            clearOtherPrimaryRolesAndFlush(account.getUsername(), primaryRoleCode, actor, now);
        }

        List<AccountRoleResponse> responses = roleCodes.stream()
                .map(roleCode -> {
                    Role targetRole = requireActiveRole(roleCode);
                    enforceAssignmentScope(authentication, account, targetRole);
                    AccountRole accountRole = accountRoleRepository.findByAccountUsernameAndRoleRoleCode(account.getUsername(), targetRole.getRoleCode())
                            .orElseGet(AccountRole::new);
                    boolean primary = primaryRoleCode != null && primaryRoleCode.equals(targetRole.getRoleCode());
                    applyAssignment(accountRole, account, targetRole, primary, request.getAssignedAt(), request.getExpiresAt(), actor, now);
                    return toResponse(accountRoleRepository.save(accountRole));
                })
                .toList();

        notifyAccountRoleChanged(account.getUsername(), "ACCOUNT_ROLE_BULK_ASSIGNED");
        return responses;
    }

    @Transactional(readOnly = true)
    public Page<AccountRoleResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest searchRequest = request == null ? new BaseSearchRequest() : request;
        List<AccountRoleResponse> manageable = accountRoleRepository.findAll().stream()
                .filter(accountRole -> canManage(authentication, accountRole.getAccount(), accountRole.getRole()))
                .map(this::toResponse)
                .filter(response -> matchesFilters(response, searchRequest))
                .toList();
        return page(searchRequest, manageable);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> findAssignableRoles(String username, Authentication authentication) {
        String normalizedUsername = commonValidationHelper.normalizeOptionalText(username);
        Account targetAccount = normalizedUsername == null ? null : requireAccount(normalizedUsername);
        Set<String> assignedRoleCodes = targetAccount == null
                ? Set.of()
                : accountRoleRepository.findByAccountUsername(targetAccount.getUsername()).stream()
                .filter(accountRole -> accountRole.getRole() != null)
                .map(accountRole -> accountRole.getRole().getRoleCode())
                .collect(java.util.stream.Collectors.toSet());
        return roleRepository.findAll().stream()
                .filter(role -> Boolean.TRUE.equals(role.getStatus()))
                .filter(role -> !assignedRoleCodes.contains(role.getRoleCode()))
                .filter(role -> targetAccount == null ? canAssignRole(authentication, role) : canManage(authentication, targetAccount, role))
                .map(this::toRoleResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountRoleResponse findById(String id, Authentication authentication) {
        AccountRole entity = require(id);
        if (!canManage(authentication, entity.getAccount(), entity.getRole())) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền xem vai trò của account này");
        }
        return toResponse(entity);
    }

    @Transactional
    public AccountRoleResponse update(String id, AccountRoleUpdateRequest request, Authentication authentication) {
        AccountRole entity = require(id);
        Account account = request.getUsername() == null ? entity.getAccount() : requireAccount(request.getUsername());
        Role role = request.getRoleCode() == null ? entity.getRole() : requireActiveRole(request.getRoleCode());
        enforceAssignmentScope(authentication, account, role);
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật vai trò tài khoản");
        OffsetDateTime now = OffsetDateTime.now();
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            clearOtherPrimaryRolesAndFlush(account.getUsername(), role.getRoleCode(), actor, now);
        }
        entity.setAccount(account);
        entity.setRole(role);
        if (request.getIsPrimary() != null) entity.setIsPrimary(request.getIsPrimary());
        if (request.getAssignedAt() != null) entity.setAssignedAt(request.getAssignedAt());
        if (request.getExpiresAt() != null) entity.setExpiresAt(request.getExpiresAt());
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(actor);
        notifyAccountRoleChanged(account.getUsername(), "ACCOUNT_ROLE_UPDATED");
        return toResponse(entity);
    }

    @Transactional
    public AccountRoleResponse toggleStatus(String id, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        AccountRole entity = require(id);
        enforceAssignmentScope(authentication, entity.getAccount(), entity.getRole());
        String targetUsername = entity.getAccount().getUsername();
        Set<String> recipients = toggleNotificationRecipientResolver == null ? Set.of() : toggleNotificationRecipientResolver.byAccount(targetUsername);
        boolean disablingPrimaryRole = Boolean.TRUE.equals(entity.getStatus()) && Boolean.TRUE.equals(entity.getIsPrimary());
        entity.setStatus(!Boolean.TRUE.equals(entity.getStatus()));
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái vai trò tài khoản"));
        if (disablingPrimaryRole) {
            promoteHighestActiveRoleAsPrimary(targetUsername, entity.getId(), entity.getUpdatedBy(), entity.getUpdatedAt());
        }
        notifyUsers(recipients, "ACCOUNT_ROLE_TOGGLED");
        if (toggleNotificationDispatcher != null) {
            toggleNotificationDispatcher.dispatch(recipients, "ACCOUNT_ROLE_TOGGLED", roleLabel(entity.getRole()), Boolean.TRUE.equals(entity.getStatus()), currentUsername(authentication));
        }
        return toResponse(entity);
    }

    private void notifyAccountRoleChanged(String username, String reason) {
        if (authRefreshNotifier != null) {
            authRefreshNotifier.notifyAccount(username, reason);
        }
    }

    private void notifyUsers(Set<String> recipients, String reason) {
        if (authRefreshNotifier != null) {
            authRefreshNotifier.notifyUsers(recipients, reason);
        }
    }

    private String roleLabel(Role role) {
        if (role == null) {
            return "vai trò chưa xác định";
        }
        String name = commonValidationHelper.normalizeOptionalText(role.getRoleName());
        String code = commonValidationHelper.normalizeOptionalText(role.getRoleCode());
        if (name != null) {
            return name;
        }
        return name != null ? name : code != null ? code : "vai trò chưa xác định";
    }

    private Account requireAccount(String username) {
        return accountRepository.findById(username)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy tài khoản " + username));
    }

    private Role requireActiveRole(String roleCode) {
        return roleRepository.findByRoleCodeAndStatusTrue(roleCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò " + roleCode));
    }

    private AccountRole require(String id) {
        return accountRoleRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy account role " + id));
    }

    private void applyAssignment(
            AccountRole accountRole,
            Account account,
            Role targetRole,
            boolean primary,
            OffsetDateTime assignedAt,
            OffsetDateTime expiresAt,
            String actor,
            OffsetDateTime now
    ) {
        boolean newRecord = accountRole.getId() == null;
        accountRole.setAccount(account);
        accountRole.setRole(targetRole);
        accountRole.setIsPrimary(primary);
        accountRole.setAssignedBy(actor);
        accountRole.setAssignedAt(assignedAt == null ? now : assignedAt);
        accountRole.setExpiresAt(expiresAt);
        accountRole.setStatus(Boolean.TRUE);
        accountRole.setUpdatedAt(now);
        accountRole.setUpdatedBy(actor);
        if (newRecord) {
            accountRole.setCreatedAt(now);
            accountRole.setCreatedBy(actor);
        }
    }

    private void clearOtherPrimaryRolesAndFlush(String username, String primaryRoleCode, String actor, OffsetDateTime now) {
        accountRoleRepository.findByAccountUsername(username).stream()
                .filter(accountRole -> accountRole.getRole() != null)
                .filter(accountRole -> !primaryRoleCode.equals(accountRole.getRole().getRoleCode()))
                .filter(accountRole -> Boolean.TRUE.equals(accountRole.getIsPrimary()))
                .forEach(accountRole -> {
                    accountRole.setIsPrimary(Boolean.FALSE);
                    accountRole.setUpdatedAt(now);
                    accountRole.setUpdatedBy(actor);
                });
        accountRoleRepository.flush();
    }

    private void promoteHighestActiveRoleAsPrimary(String username, String disabledAccountRoleId, String actor, OffsetDateTime now) {
        List<AccountRole> accountRoles = accountRoleRepository.findByAccountUsername(username);
        accountRoles.stream()
                .filter(accountRole -> Boolean.TRUE.equals(accountRole.getIsPrimary()))
                .forEach(accountRole -> {
                    accountRole.setIsPrimary(Boolean.FALSE);
                    accountRole.setUpdatedAt(now);
                    accountRole.setUpdatedBy(actor);
                });
        accountRoles.stream()
                .filter(accountRole -> !disabledAccountRoleId.equals(accountRole.getId()))
                .filter(accountRole -> Boolean.TRUE.equals(accountRole.getStatus()))
                .filter(accountRole -> accountRole.getRole() != null)
                .filter(accountRole -> Boolean.TRUE.equals(accountRole.getRole().getStatus()))
                .filter(accountRole -> accountRole.getExpiresAt() == null || accountRole.getExpiresAt().isAfter(now))
                .min(Comparator.comparingInt(accountRole -> roleOrder(accountRole.getRole())))
                .ifPresent(accountRole -> {
                    accountRole.setIsPrimary(Boolean.TRUE);
                    accountRole.setUpdatedAt(now);
                    accountRole.setUpdatedBy(actor);
                });
    }

    private boolean canManage(Authentication authentication, Account targetAccount, Role targetRole) {
        try {
            enforceAssignmentScope(authentication, targetAccount, targetRole);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean canAssignRole(Authentication authentication, Role role) {
        if (hasRole(authentication, ROLE_SUPER_ADMIN)) {
            return isSuperAdminAssignableRole(role);
        }
        String actorRoleCode = AuthorityChecks.hasAuthority(authentication, "system.account_role.assign.diocese") ? ROLE_ADMIN_DIOCESE
                : AuthorityChecks.hasAuthority(authentication, "system.account_role.assign.deanery") ? ROLE_ADMIN_DEANERY
                : AuthorityChecks.hasAuthority(authentication, "system.account_role.assign.parish") ? ROLE_ADMIN_PARISH : null;
        if (actorRoleCode == null) {
            return false;
        }
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

    private void enforceAssignmentScope(Authentication authentication, Account targetAccount, Role targetRole) {
        if (unitOperationalStatusService != null) {
            unitOperationalStatusService.requireAccountWritable(targetAccount.getUsername());
        }
        if (currentUsername(authentication).equals(targetAccount.getUsername())) {
            throw commonValidationHelper.forbidden("Quản trị viên không được tự gán vai trò cho tài khoản của mình");
        }

        if (hasRole(authentication, ROLE_SUPER_ADMIN)) {
            enforceSuperAdminScope(targetAccount, targetRole);
            return;
        }

        accountManagementAuthorizationHelper.requireTargetAccountNotAboveActor(authentication, targetAccount);

        if (AuthorityChecks.hasAuthority(authentication, "system.account_role.assign.diocese")) {
            enforceScopedSystemRoleAssignment(ROLE_ADMIN_DIOCESE, targetRole);
            UUID actorDioceseId = accountRepository.findActiveDioceseIdByUsername(currentUsername(authentication))
                    .orElseThrow(() -> commonValidationHelper.forbidden("Tài khoản hiện tại không có scope giáo phận hợp lệ"));
            UUID targetDioceseId = accountRepository.findActiveDioceseIdByUsername(targetAccount.getUsername())
                    .orElseThrow(() -> commonValidationHelper.forbidden("Account đích chưa có scope giáo phận hợp lệ để gán vai trò"));
            if (!actorDioceseId.equals(targetDioceseId)) {
                throw commonValidationHelper.forbidden("Không thể gán vai trò cho account ngoài giáo phận hiện tại");
            }
            enforceHierarchy(authentication, targetRole, ROLE_ADMIN_DIOCESE);
            return;
        }

        if (AuthorityChecks.hasAuthority(authentication, "system.account_role.assign.deanery")) {
            enforceScopedSystemRoleAssignment(ROLE_ADMIN_DEANERY, targetRole);
            UUID actorDeaneryId = accountRepository.findActiveDeaneryIdByUsername(currentUsername(authentication))
                    .orElseThrow(() -> commonValidationHelper.forbidden("Tài khoản hiện tại không có phạm vi giáo hạt hợp lệ"));
            UUID targetDeaneryId = accountRepository.findActiveDeaneryIdByUsername(targetAccount.getUsername())
                    .orElseThrow(() -> commonValidationHelper.forbidden("Tài khoản đích chưa có phạm vi giáo hạt hợp lệ để gán vai trò"));
            if (!actorDeaneryId.equals(targetDeaneryId)) {
                throw commonValidationHelper.forbidden("Không thể gán vai trò cho tài khoản ngoài giáo hạt hiện tại");
            }
            enforceHierarchy(authentication, targetRole, ROLE_ADMIN_DEANERY);
            return;
        }

        if (AuthorityChecks.hasAuthority(authentication, "system.account_role.assign.parish")) {
            enforceScopedSystemRoleAssignment(ROLE_ADMIN_PARISH, targetRole);
            UUID actorParishId = accountRepository.findActiveParishIdByUsername(currentUsername(authentication))
                    .orElseThrow(() -> commonValidationHelper.forbidden("Tài khoản hiện tại không có phạm vi giáo xứ hợp lệ"));
            UUID targetParishId = accountRepository.findActiveParishIdByUsername(targetAccount.getUsername())
                    .orElseThrow(() -> commonValidationHelper.forbidden("Tài khoản đích chưa có phạm vi giáo xứ hợp lệ để gán vai trò"));
            if (!actorParishId.equals(targetParishId)) {
                throw commonValidationHelper.forbidden("Không thể gán vai trò cho tài khoản ngoài giáo xứ hiện tại");
            }
            enforceHierarchy(authentication, targetRole, ROLE_ADMIN_PARISH);
            return;
        }

        throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền gán vai trò");
    }

    private void enforceScopedSystemRoleAssignment(String actorRoleCode, Role targetRole) {
        if (!canAssignSystemRole(actorRoleCode, targetRole)) {
            throw commonValidationHelper.forbidden("Không thể gán vai trò ngoài phạm vi phân cấp");
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
            throw commonValidationHelper.forbidden("SUPER_ADMIN chỉ được gán vai trò SUPER_ADMIN, ADMIN_DIOCESE hoặc vai trò custom do super-admin tạo");
        }
        if (!isSuperAdminManagedAccount(targetAccount, targetRole)) {
            throw commonValidationHelper.forbidden("SUPER_ADMIN chỉ được gán vai trò cho tài khaonr SUPER_ADMIN, ADMIN_DIOCESE hoặc tài khoản có vai trò t tạo do super-admin tạo");
        }
        if (ROLE_SUPER_ADMIN.equals(targetRole.getRoleCode()) && !isRootAccount(targetAccount)) {
            throw commonValidationHelper.forbidden("Vai trò SUPER_ADMIN chỉ được gán cho tài khoản không trực thuộc giáo phận/giáo hạt/giáo xứ");
        }
        if (ROLE_ADMIN_DIOCESE.equals(targetRole.getRoleCode()) && targetAccount.getDiocese() == null) {
            throw commonValidationHelper.forbidden("Vai trò ADMIN_DIOCESE chỉ được gán cho tài khoản trực thuộc giáo phận");
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
            throw commonValidationHelper.forbidden("Không thể gán vai trò cao hơn phạm vi của tài khoản hiện tại");
        }
    }

    private Integer actorHierarchyOrder(Authentication authentication, String fallbackActorRoleCode) {
        Integer actorOrder = accountManagementAuthorizationHelper.currentActorHighestRoleOrder(authentication);
        if (actorOrder != null) {
            return actorOrder;
        }
        return roleRepository.findByRoleCodeAndStatusTrue(fallbackActorRoleCode)
                .map(Role::getDisplayOrder)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò " + fallbackActorRoleCode));
    }

    private int roleOrder(Role role) {
        return role.getDisplayOrder() == null ? Integer.MAX_VALUE : role.getDisplayOrder();
    }


    private boolean hasRole(Authentication authentication, String roleCode) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserDetails principal)) {
            return false;
        }
        return principal.getRoles().stream().anyMatch(roleCode::equals);
    }

    private String currentUsername(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUserDetails principal)) {
            throw commonValidationHelper.forbidden("Không xác định được tài khoản hiện tại");
        }
        return principal.getUsername();
    }

    private AccountRoleResponse toResponse(AccountRole accountRole) {
        return AccountRoleResponse.builder()
                .id(accountRole.getId())
                .username(accountRole.getAccount().getUsername())
                .roleCode(accountRole.getRole().getRoleCode())
                .roleName(accountRole.getRole().getRoleName())
                .leaderFullName(accountRole.getAccount().getLeader() == null
                        ? null
                        : phucuong.ht.common.utils.AuditActorNameResolver.displayName(accountRole.getAccount().getLeader()))
                .isPrimary(accountRole.getIsPrimary())
                .assignedBy(accountRole.getAssignedBy())
                .assignedByName(auditActorNameResolver.resolve(accountRole.getAssignedBy()))
                .assignedAt(accountRole.getAssignedAt())
                .expiresAt(accountRole.getExpiresAt())
                .status(accountRole.getStatus())
                .createdAt(accountRole.getCreatedAt())
                .createdBy(accountRole.getCreatedBy())
                .createdByName(auditActorNameResolver.resolve(accountRole.getCreatedBy()))
                .updatedAt(accountRole.getUpdatedAt())
                .updatedBy(accountRole.getUpdatedBy())
                .updatedByName(auditActorNameResolver.resolve(accountRole.getUpdatedBy()))
                .build();
    }

    private boolean matchesFilters(AccountRoleResponse response, BaseSearchRequest request) {
        if (request.getStatus() != null && !request.getStatus().equals(response.getStatus())) return false;
        String search = request.getSearch() == null ? "" : request.getSearch().trim().toLowerCase(Locale.ROOT);
        if (!search.isBlank()) {
            String haystack = String.join(" ",
                    safe(response.getUsername()),
                    safe(response.getRoleCode()),
                    safe(response.getAssignedBy())
            ).toLowerCase(Locale.ROOT);
            if (!haystack.contains(search)) return false;
        }

        Map<String, Object> filters = request.getFilters() == null ? Map.of() : request.getFilters();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue()).trim();
            if (value.isBlank()) continue;
            switch (entry.getKey()) {
                case "roleCode", "role.roleCode" -> {
                    if (!value.equals(response.getRoleCode())) return false;
                }
                case "username", "account.username" -> {
                    if (!value.equals(response.getUsername())) return false;
                }
                case "isPrimary" -> {
                    if (!Boolean.valueOf(value).equals(Boolean.TRUE.equals(response.getIsPrimary()))) return false;
                }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    private Page<AccountRoleResponse> page(BaseSearchRequest request, List<AccountRoleResponse> accountRoles) {
        return InMemoryPager.sortedPage(request, accountRoles);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private RoleResponse toRoleResponse(Role role) {
        return RoleResponse.builder()
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .isSystem(role.getIsSystem())
                .displayOrder(role.getDisplayOrder())
                .status(role.getStatus())
                .createdAt(role.getCreatedAt())
                .createdBy(role.getCreatedBy())
                .createdByName(auditActorNameResolver.resolve(role.getCreatedBy()))
                .updatedAt(role.getUpdatedAt())
                .updatedBy(role.getUpdatedBy())
                .updatedByName(auditActorNameResolver.resolve(role.getUpdatedBy()))
                .build();
    }
}
