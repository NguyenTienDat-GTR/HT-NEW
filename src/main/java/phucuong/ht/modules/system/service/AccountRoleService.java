package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountRole;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.request.AccountRoleBulkRequest;
import phucuong.ht.modules.system.model.request.AccountRoleCreateRequest;
import phucuong.ht.modules.system.model.request.AccountRoleUpdateRequest;
import phucuong.ht.modules.system.model.response.AccountRoleResponse;
import phucuong.ht.modules.system.model.response.RoleResponse;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountRoleService {

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ADMIN_DIOCESE = "ADMIN_DIOCESE";
    private static final String ROLE_ADMIN_DEANERY = "ADMIN_DEANERY";
    private static final String ROLE_ADMIN_PARISH = "ADMIN_PARISH";
    private static final Set<String> SUPER_ADMIN_MANAGED_SYSTEM_ROLES = Set.of(ROLE_SUPER_ADMIN, ROLE_ADMIN_DIOCESE);

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final RoleRepository roleRepository;
    private final CommonValidationHelper commonValidationHelper;

    @Transactional
    public AccountRoleResponse assignRole(AccountRoleCreateRequest request, Authentication authentication) {
        Account account = requireAccount(request.getUsername());
        Role targetRole = requireActiveRole(request.getRoleCode());

        enforceAssignmentScope(authentication, account, targetRole);

        OffsetDateTime now = OffsetDateTime.now();
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người gán vai trò");

        AccountRole accountRole = accountRoleRepository.findByAccountUsernameAndRoleRoleCode(account.getUsername(), targetRole.getRoleCode())
                .orElseGet(AccountRole::new);
        applyAssignment(accountRole, account, targetRole, Boolean.TRUE.equals(request.getIsPrimary()), request.getAssignedAt(), request.getExpiresAt(), actor, now);
        AccountRole saved = accountRoleRepository.save(accountRole);
        if (Boolean.TRUE.equals(saved.getIsPrimary())) {
            clearOtherPrimaryRoles(account.getUsername(), saved.getRole().getRoleCode(), actor);
        }
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

        if (primaryRoleCode != null) {
            clearOtherPrimaryRoles(account.getUsername(), primaryRoleCode, actor);
        }
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
        entity.setAccount(account);
        entity.setRole(role);
        if (request.getIsPrimary() != null) entity.setIsPrimary(request.getIsPrimary());
        if (request.getAssignedAt() != null) entity.setAssignedAt(request.getAssignedAt());
        if (request.getExpiresAt() != null) entity.setExpiresAt(request.getExpiresAt());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật vai trò tài khoản");
        entity.setUpdatedBy(actor);
        if (Boolean.TRUE.equals(entity.getIsPrimary())) {
            clearOtherPrimaryRoles(account.getUsername(), role.getRoleCode(), actor);
        }
        return toResponse(entity);
    }

    @Transactional
    public AccountRoleResponse toggleStatus(String id, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        AccountRole entity = require(id);
        enforceAssignmentScope(authentication, entity.getAccount(), entity.getRole());
        entity.setStatus(!Boolean.TRUE.equals(entity.getStatus()));
        entity.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái vai trò tài khoản"));
        return toResponse(entity);
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

    private void clearOtherPrimaryRoles(String username, String primaryRoleCode, String actor) {
        accountRoleRepository.findByAccountUsername(username).stream()
                .filter(accountRole -> accountRole.getRole() != null)
                .filter(accountRole -> !primaryRoleCode.equals(accountRole.getRole().getRoleCode()))
                .filter(accountRole -> Boolean.TRUE.equals(accountRole.getIsPrimary()))
                .forEach(accountRole -> {
                    accountRole.setIsPrimary(Boolean.FALSE);
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
        if (ROLE_ADMIN_DIOCESE.equals(role.getRoleCode())) {
            return false;
        }
        String actorRoleCode = hasAuthority(authentication, "system.account_role.assign.diocese") ? ROLE_ADMIN_DIOCESE
                : hasAuthority(authentication, "system.account_role.assign.deanery") ? ROLE_ADMIN_DEANERY
                : hasAuthority(authentication, "system.account_role.assign.parish") ? ROLE_ADMIN_PARISH : null;
        if (actorRoleCode == null) {
            return false;
        }
        return roleRepository.findByRoleCodeAndStatusTrue(actorRoleCode)
                .map(actorRole -> {
                    Integer actorOrder = actorRole.getDisplayOrder();
                    Integer targetOrder = role.getDisplayOrder();
                    return actorOrder != null && targetOrder != null && targetOrder >= actorOrder;
                })
                .orElse(false);
    }

    private void enforceAssignmentScope(Authentication authentication, Account targetAccount, Role targetRole) {
        if (currentUsername(authentication).equals(targetAccount.getUsername())) {
            throw commonValidationHelper.forbidden("Quản trị viên không được tự gán vai trò cho tài khoản của mình");
        }

        if (hasRole(authentication, ROLE_SUPER_ADMIN)) {
            enforceSuperAdminScope(targetAccount, targetRole);
            return;
        }

        if (ROLE_ADMIN_DIOCESE.equals(targetRole.getRoleCode())) {
            throw commonValidationHelper.forbidden("Chỉ SUPER_ADMIN được gán vai trò ADMIN_DIOCESE");
        }

        if (hasAuthority(authentication, "system.account_role.assign.diocese")) {
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

        if (hasAuthority(authentication, "system.account_role.assign.deanery")) {
            UUID actorDeaneryId = accountRepository.findActiveDeaneryIdByUsername(currentUsername(authentication))
                    .orElseThrow(() -> commonValidationHelper.forbidden("Tài khoản hiện tại không có scope giáo hạt hợp lệ"));
            UUID targetDeaneryId = accountRepository.findActiveDeaneryIdByUsername(targetAccount.getUsername())
                    .orElseThrow(() -> commonValidationHelper.forbidden("Account đích chưa có scope giáo hạt hợp lệ để gán vai trò"));
            if (!actorDeaneryId.equals(targetDeaneryId)) {
                throw commonValidationHelper.forbidden("Không thể gán vai trò cho account ngoài giáo hạt hiện tại");
            }
            enforceHierarchy(authentication, targetRole, ROLE_ADMIN_DEANERY);
            return;
        }

        if (hasAuthority(authentication, "system.account_role.assign.parish")) {
            UUID actorParishId = accountRepository.findActiveParishIdByUsername(currentUsername(authentication))
                    .orElseThrow(() -> commonValidationHelper.forbidden("Tài khoản hiện tại không có scope giáo xứ hợp lệ"));
            UUID targetParishId = accountRepository.findActiveParishIdByUsername(targetAccount.getUsername())
                    .orElseThrow(() -> commonValidationHelper.forbidden("Account đích chưa có scope giáo xứ hợp lệ để gán vai trò"));
            if (!actorParishId.equals(targetParishId)) {
                throw commonValidationHelper.forbidden("Không thể gán vai trò cho account ngoài giáo xứ hiện tại");
            }
            enforceHierarchy(authentication, targetRole, ROLE_ADMIN_PARISH);
            return;
        }

        throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền gán vai trò");
    }

    private void enforceSuperAdminScope(Account targetAccount, Role targetRole) {
        if (!isSuperAdminAssignableRole(targetRole)) {
            throw commonValidationHelper.forbidden("SUPER_ADMIN chỉ được gán vai trò SUPER_ADMIN, ADMIN_DIOCESE hoặc vai trò custom do super-admin tạo");
        }
        if (!isSuperAdminManagedAccount(targetAccount)) {
            throw commonValidationHelper.forbidden("SUPER_ADMIN chỉ được gán vai trò cho account SUPER_ADMIN, ADMIN_DIOCESE hoặc account có role custom do super-admin tạo");
        }
        if (ROLE_SUPER_ADMIN.equals(targetRole.getRoleCode()) && !isRootAccount(targetAccount)) {
            throw commonValidationHelper.forbidden("Vai trò SUPER_ADMIN chỉ được gán cho account không trực thuộc giáo phận/giáo hạt/giáo xứ");
        }
        if (ROLE_ADMIN_DIOCESE.equals(targetRole.getRoleCode()) && targetAccount.getDiocese() == null) {
            throw commonValidationHelper.forbidden("Vai trò ADMIN_DIOCESE chỉ được gán cho account trực thuộc giáo phận");
        }
    }

    private boolean isSuperAdminManagedAccount(Account targetAccount) {
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
        Role actorRole = roleRepository.findByRoleCodeAndStatusTrue(actorRoleCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò " + actorRoleCode));

        Integer actorOrder = actorRole.getDisplayOrder();
        Integer targetOrder = targetRole.getDisplayOrder();
        if (actorOrder == null || targetOrder == null || targetOrder < actorOrder) {
            throw commonValidationHelper.forbidden("Không thể gán vai trò cao hơn phạm vi của tài khoản hiện tại");
        }
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
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
                .isPrimary(accountRole.getIsPrimary())
                .assignedBy(accountRole.getAssignedBy())
                .assignedAt(accountRole.getAssignedAt())
                .expiresAt(accountRole.getExpiresAt())
                .status(accountRole.getStatus())
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
        int page = request == null ? 0 : request.getPage();
        int size = request == null ? 10 : request.getSize();
        int start = Math.min(page * size, accountRoles.size());
        int end = Math.min(start + size, accountRoles.size());
        return new PageImpl<>(accountRoles.subList(start, end), PageRequest.of(page, size), accountRoles.size());
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
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}
