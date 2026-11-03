package phucuong.ht.modules.system.service;

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
public class AccountRoleService {

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final RoleRepository roleRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final AccountManagementAuthorizationHelper accountManagementAuthorizationHelper;
    private final phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;
    private final RoleAssignmentPolicy assignmentPolicy;
    public AccountRoleService(AccountRepository accountRepository, AccountRoleRepository accountRoleRepository, RoleRepository roleRepository,
                              CommonValidationHelper commonValidationHelper, AccountManagementAuthorizationHelper accountManagementAuthorizationHelper,
                              phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver) {
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.roleRepository = roleRepository;
        this.commonValidationHelper = commonValidationHelper;
        this.accountManagementAuthorizationHelper = accountManagementAuthorizationHelper;
        this.auditActorNameResolver = auditActorNameResolver;
        this.assignmentPolicy = new RoleAssignmentPolicy(accountRepository, accountRoleRepository, roleRepository,
                commonValidationHelper, accountManagementAuthorizationHelper);
    }

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
        requireRoleCombination(account.getUsername(), List.of(targetRole.getRoleCode()), Set.of());

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

        requireRoleCombination(account.getUsername(), roleCodes, Set.of());

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
        if (Boolean.TRUE.equals(entity.getStatus())) {
            Set<String> replaced = entity.getAccount() != null && entity.getAccount().getUsername().equals(account.getUsername()) && entity.getRole() != null
                    ? Set.of(entity.getRole().getRoleCode())
                    : Set.of();
            requireRoleCombination(account.getUsername(), List.of(role.getRoleCode()), replaced);
        }
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
        if (!Boolean.TRUE.equals(entity.getStatus())) {
            requireRoleCombination(entity.getAccount().getUsername(), List.of(entity.getRole().getRoleCode()), Set.of());
        }
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

    // Vai trò đang hiệu lực của tài khoản sau khi thêm addedRoleCodes (và bỏ removedRoleCodes) phải đúng quy tắc kết hợp vai trò.
    private void requireRoleCombination(String username, Collection<String> addedRoleCodes, Set<String> removedRoleCodes) {
        Set<String> resulting = new LinkedHashSet<>(accountRoleRepository.findActiveRoleCodesByUsername(username));
        resulting.removeAll(removedRoleCodes);
        resulting.addAll(addedRoleCodes);
        AccountRoleCombinationPolicy.require(resulting, commonValidationHelper);
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
        return assignmentPolicy.canAssign(authentication, role);
    }

    private void enforceAssignmentScope(Authentication authentication, Account targetAccount, Role targetRole) {
        if (unitOperationalStatusService != null) {
            unitOperationalStatusService.requireAccountWritable(targetAccount.getUsername());
        }
        assignmentPolicy.enforce(authentication, targetAccount, targetRole);
    }

                                            private int roleOrder(Role role) {
        return role.getDisplayOrder() == null ? Integer.MAX_VALUE : role.getDisplayOrder();
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
