package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
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
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.notification.service.ToggleNotificationDispatcher;
import phucuong.ht.modules.notification.service.ToggleNotificationRecipientResolver;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountPermissionOverride;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.enums.PermissionEffect;
import phucuong.ht.modules.system.model.enums.PermissionRevocationReason;
import phucuong.ht.modules.system.model.request.AccountPermissionOverrideBulkRequest;
import phucuong.ht.modules.system.model.request.AccountPermissionOverrideRevokeRequest;
import phucuong.ht.modules.system.model.request.AccountPermissionOverrideCreateRequest;
import phucuong.ht.modules.system.model.request.AccountPermissionOverrideUpdateRequest;
import phucuong.ht.modules.system.model.response.AccountPermissionOverrideResponse;
import phucuong.ht.modules.system.model.response.PermissionResponse;
import phucuong.ht.modules.system.repository.AccountPermissionOverrideRepository;
import phucuong.ht.modules.system.repository.RolePermissionRepository;
import phucuong.ht.modules.system.repository.SystemPermissionRepository;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AccountPermissionOverrideManagementService {

    private static final List<String> ADMIN_ROLE_CODES = List.of("ADMIN_DIOCESE", "ADMIN_DEANERY", "ADMIN_PARISH");
    private static final java.util.Map<String, String> REPOSITORY_FIELD_ALIASES = java.util.Map.ofEntries(
            java.util.Map.entry("username", "account.username"),
            java.util.Map.entry("permissionCode", "permission.permissionCode"),
            java.util.Map.entry("permissionName", "permission.permissionName"),
            java.util.Map.entry("moduleCode", "permission.module.moduleCode"),
            java.util.Map.entry("resourceCode", "permission.resource.resourceCode"),
            java.util.Map.entry("actionCode", "permission.action.actionCode"),
            java.util.Map.entry("scopeCode", "permission.scope.scopeCode")
    );

    private final AccountPermissionOverrideRepository accountPermissionOverrideRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final SystemPermissionRepository systemPermissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final SystemAdminAuthorizationHelper authorizationHelper;
    @Autowired(required = false)
    private UnitOperationalStatusService unitOperationalStatusService;
    @Autowired(required = false)
    private ToggleNotificationRecipientResolver toggleNotificationRecipientResolver;
    @Autowired(required = false)
    private ToggleNotificationDispatcher toggleNotificationDispatcher;
    @Autowired(required = false)
    private RbacAuthRefreshNotifier authRefreshNotifier;

    @Transactional
    public AccountPermissionOverrideResponse create(AccountPermissionOverrideCreateRequest request, Authentication authentication) {
        Account account = accountRepository.findActiveByUsername(request.getUsername())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy tài khoản: " + request.getUsername()));
        requireAccountWritable(account.getUsername());
        requireValidSuperAdminTarget(authentication, account);
        Permission permission = systemPermissionRepository.findById(request.getPermissionCode())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy quyền: " + request.getPermissionCode()));
        if (!Boolean.TRUE.equals(permission.getStatus())) {
            throw commonValidationHelper.conflict("Không thể gán quyền đang ngừng hoạt động");
        }

        authorizationHelper.requireAccountPermissionWriteAccess(authentication, request.getUsername(), permission);
        rejectDenyOfRequiredAccountRoleAssignment(account, request.getPermissionCode(), request.getEffect());

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo phân quyền cho tài khoản");
        AccountPermissionOverride existing = accountPermissionOverrideRepository.findByUsernameAndPermissionCode(request.getUsername(), request.getPermissionCode()).orElse(null);
        if (existing != null) {
            if (!PermissionValiditySupport.isPermanentlyRevoked(existing.getRevokedAt(), existing.getExpiresAt(), OffsetDateTime.now())) {
                throw commonValidationHelper.conflict("Phân quyền riêng cho tài khoản đã tồn tại");
            }
            // Quyền đã thu hồi/hết hạn không bật lại được: phân quyền mới thay thế bản ghi cũ.
            existing.setEffect(request.getEffect());
            existing.setReason(commonValidationHelper.normalizeOptionalText(request.getReason()));
            existing.setExpiresAt(request.getExpiresAt());
            reissue(existing, actor);
            notifyAccountPermissionChanged(request.getUsername(), "ACCOUNT_PERMISSION_CREATED");
            return toResponse(existing);
        }
        AccountPermissionOverride override = accountPermissionOverrideRepository.create(request, actor);
        override.setStatus(true);
        notifyAccountPermissionChanged(request.getUsername(), "ACCOUNT_PERMISSION_CREATED");
        return toResponse(override);
    }

    @Transactional
    public List<AccountPermissionOverrideResponse> bulkAssign(AccountPermissionOverrideBulkRequest request, Authentication authentication) {
        Account account = accountRepository.findActiveByUsername(request.getUsername())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy tài khoản: " + request.getUsername()));
        requireAccountWritable(account.getUsername());
        requireValidSuperAdminTarget(authentication, account);
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo phân quyền cho tài khoản");
        OffsetDateTime now = OffsetDateTime.now();
        List<AccountPermissionOverrideResponse> responses = new ArrayList<>();

        for (String permissionCode : new LinkedHashSet<>(request.getPermissionCodes())) {
            Permission permission = systemPermissionRepository.findById(permissionCode)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy quyền: " + permissionCode));
            if (!Boolean.TRUE.equals(permission.getStatus())) {
                throw commonValidationHelper.conflict("Không thể gán quyền đang ngừng hoạt động: " + permissionCode);
            }
            authorizationHelper.requireAccountPermissionWriteAccess(authentication, request.getUsername(), permission);
            rejectDenyOfRequiredAccountRoleAssignment(account, permissionCode, request.getEffect());

            AccountPermissionOverride override = accountPermissionOverrideRepository.findByUsernameAndPermissionCode(request.getUsername(), permissionCode)
                    .orElseGet(() -> createOverride(account, permission, actor));
            if (PermissionValiditySupport.isPermanentlyRevoked(override.getRevokedAt(), override.getExpiresAt(), now)) {
                reissue(override, actor);
            }
            override.setEffect(request.getEffect());
            override.setReason(commonValidationHelper.normalizeRequiredText(request.getReason()));
            override.setExpiresAt(request.getExpiresAt());
            override.setStatus(true);
            override.setUpdatedAt(now);
            override.setUpdatedBy(actor);
            responses.add(toResponse(override));
        }

        notifyAccountPermissionChanged(request.getUsername(), "ACCOUNT_PERMISSION_BULK_ASSIGNED");
        return responses;
    }

    /** Thu hồi nhiều quyền riêng của tài khoản: đánh dấu thu hồi vĩnh viễn, giữ lại bản ghi (muốn cấp lại phải phân quyền mới). */
    @Transactional
    public int bulkRevoke(AccountPermissionOverrideRevokeRequest request, Authentication authentication) {
        Account account = accountRepository.findActiveByUsername(request.getUsername())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy tài khoản: " + request.getUsername()));
        requireAccountWritable(account.getUsername());
        requireValidSuperAdminTarget(authentication, account);
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người thu hồi phân quyền của tài khoản");
        int revoked = 0;
        for (String permissionCode : new LinkedHashSet<>(request.getPermissionCodes())) {
            AccountPermissionOverride override = accountPermissionOverrideRepository.findByUsernameAndPermissionCode(request.getUsername(), permissionCode)
                    .orElseThrow(() -> commonValidationHelper.notFound("Tài khoản chưa được gán quyền riêng: " + permissionCode));
            authorizationHelper.requireAccountPermissionWriteAccess(authentication, request.getUsername(), override.getPermission());
            OffsetDateTime now = OffsetDateTime.now();
            if (PermissionValiditySupport.isPermanentlyRevoked(override.getRevokedAt(), override.getExpiresAt(), now)) {
                throw commonValidationHelper.conflict("Quyền đã bị thu hồi hoặc hết hạn: " + permissionCode);
            }
            override.setRevokedAt(now);
            override.setRevokedBy(actor);
            override.setRevocationReason(PermissionRevocationReason.MANUAL);
            override.setUpdatedAt(now);
            override.setUpdatedBy(actor);
            revoked++;
        }
        notifyAccountPermissionChanged(request.getUsername(), "ACCOUNT_PERMISSION_BULK_REVOKED");
        return revoked;
    }

    /** Quyền riêng đã gán cho tài khoản mà tài khoản hiện tại được phép thu hồi. */
    @Transactional(readOnly = true)
    public List<PermissionResponse> findRevocablePermissions(String username, Authentication authentication) {
        Account account = accountRepository.findActiveByUsername(username)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy tài khoản: " + username));
        requireValidSuperAdminTarget(authentication, account);
        return accountPermissionOverrideRepository.findPermissionCodesByUsername(username).stream()
                .map(systemPermissionRepository::findById)
                .flatMap(java.util.Optional::stream)
                .filter(permission -> authorizationHelper.canManageAccountPermission(authentication, username, permission))
                .map(this::toPermissionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<AccountPermissionOverrideResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        List<AccountPermissionOverride> manageable = loadAllMatching(request).stream()
                .filter(override -> canReadOverride(authentication, override))
                .toList();
        return page(request, manageable);
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> findAssignablePermissions(String username, Authentication authentication) {
        Account account = accountRepository.findActiveByUsername(username)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy tài khoản: " + username));
        requireValidSuperAdminTarget(authentication, account);
        Set<String> assignedPermissionCodes = assignedPermissionCodes(username);
        return systemPermissionRepository.findActivePermissions().stream()
                .filter(permission -> !assignedPermissionCodes.contains(permission.getPermissionCode()))
                .filter(permission -> authorizationHelper.canManageAccountPermission(authentication, username, permission))
                .map(this::toPermissionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountPermissionOverrideResponse findById(String id, Authentication authentication) {
        AccountPermissionOverride override = accountPermissionOverrideRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy quyền riêng của tài khoản: " + id));
        if (!canReadOverride(authentication, override)) {
            throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền đọc phân quyền riêng này");
        }
        return toResponse(override);
    }

    @Transactional
    public AccountPermissionOverrideResponse update(String id, AccountPermissionOverrideUpdateRequest request, Authentication authentication) {
        AccountPermissionOverride override = accountPermissionOverrideRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy quyền riêng của tài khoản: " + id));
        requireAccountWritable(override.getAccount().getUsername());
        requireValidSuperAdminTarget(authentication, override.getAccount());
        authorizationHelper.requireAccountPermissionWriteAccess(authentication, override.getAccount().getUsername(), override.getPermission());
        requireNotRevoked(override);

        if (request.getEffect() != null) {
            rejectDenyOfRequiredAccountRoleAssignment(override.getAccount(), override.getPermission().getPermissionCode(), request.getEffect());
            override.setEffect(request.getEffect());
        }
        if (request.getReason() != null) {
            override.setReason(commonValidationHelper.normalizeOptionalText(request.getReason()));
        }
        if (request.getExpiresAt() != null) {
            override.setExpiresAt(request.getExpiresAt());
        }
        override.setStatus(true);
        override.setUpdatedAt(OffsetDateTime.now());
        override.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật phân quyền cho tài khoản"));
        notifyAccountPermissionChanged(override.getAccount().getUsername(), "ACCOUNT_PERMISSION_UPDATED");
        return toResponse(override);
    }

    @Transactional
    public AccountPermissionOverrideResponse toggleStatus(String id, StatusToggleRequest request, Authentication authentication) {
        AccountPermissionOverride override = accountPermissionOverrideRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy quyền riêng của tài khoản: " + id));
        requireAccountWritable(override.getAccount().getUsername());
        requireValidSuperAdminTarget(authentication, override.getAccount());
        authorizationHelper.requireAccountPermissionWriteAccess(authentication, override.getAccount().getUsername(), override.getPermission());
        requireNotRevoked(override);
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        PermissionEffect nextEffect = override.getEffect() == PermissionEffect.DENY ? PermissionEffect.ALLOW : PermissionEffect.DENY;
        rejectDenyOfRequiredAccountRoleAssignment(override.getAccount(), override.getPermission().getPermissionCode(), nextEffect);
        Set<String> recipients = toggleNotificationRecipientResolver == null ? Set.of() : toggleNotificationRecipientResolver.byAccount(override.getAccount().getUsername());
        override.setEffect(nextEffect);
        override.setStatus(true);
        override.setUpdatedAt(OffsetDateTime.now());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái phân quyền cho tài khoản");
        override.setUpdatedBy(actor);
        notifyUsers(recipients, "ACCOUNT_PERMISSION_TOGGLED");
        if (toggleNotificationDispatcher != null) {
            toggleNotificationDispatcher.dispatch(recipients, "ACCOUNT_PERMISSION_TOGGLED", permissionLabel(override.getPermission()), nextEffect == PermissionEffect.ALLOW, actor);
        }
        return toResponse(override);
    }

    private void requireNotRevoked(AccountPermissionOverride override) {
        if (PermissionValiditySupport.isPermanentlyRevoked(override.getRevokedAt(), override.getExpiresAt(), OffsetDateTime.now())) {
            throw commonValidationHelper.conflict("Phân quyền đã bị thu hồi hoặc hết hạn, không thể thay đổi; hãy tạo phân quyền mới");
        }
    }

    private void reissue(AccountPermissionOverride override, String actor) {
        override.setRevokedAt(null);
        override.setRevokedBy(null);
        override.setRevocationReason(null);
        override.setStatus(true);
        override.setUpdatedAt(OffsetDateTime.now());
        override.setUpdatedBy(actor);
    }

    private void notifyAccountPermissionChanged(String username, String reason) {
        if (authRefreshNotifier != null) {
            authRefreshNotifier.notifyAccount(username, reason);
        }
    }

    private void notifyUsers(Set<String> recipients, String reason) {
        if (authRefreshNotifier != null) {
            authRefreshNotifier.notifyUsers(recipients, reason);
        }
    }

    private String permissionLabel(Permission permission) {
        if (permission == null) {
            return "quyền chưa xác định";
        }
        String name = commonValidationHelper.normalizeOptionalText(permission.getPermissionName());
        String code = commonValidationHelper.normalizeOptionalText(permission.getPermissionCode());
        if (name != null && code != null) {
            return name + " (" + code + ")";
        }
        return name != null ? name : code != null ? code : "quyền chưa xác định";
    }

    private void requireAccountWritable(String username) {
        if (unitOperationalStatusService != null) {
            unitOperationalStatusService.requireAccountWritable(username);
        }
    }

    private BaseSearchRequest copyRequest(BaseSearchRequest request) {
        BaseSearchRequest source = request == null ? new BaseSearchRequest() : request;
        BaseSearchRequest copy = new BaseSearchRequest();
        copy.setPage(source.getPage());
        copy.setSize(source.getSize());
        copy.setSearch(source.getSearch());
        copy.setSearchFields(source.getSearchFields() == null ? List.of() : source.getSearchFields().stream().map(this::toRepositoryField).toList());
        copy.setFilters(normalizeFilters(source.getFilters()));
        copy.setStatus(source.getStatus());
        copy.setSortBy(toRepositoryField(source.getSortBy()));
        copy.setSortDirection(source.getSortDirection());
        return copy;
    }

    private LinkedHashMap<String, Object> normalizeFilters(java.util.Map<String, Object> filters) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        if (filters == null || filters.isEmpty()) {
            return normalized;
        }
        filters.forEach((key, value) -> normalized.put(toRepositoryField(key), value));
        return normalized;
    }

    private String toRepositoryField(String field) {
        if (field == null || field.isBlank()) {
            return field;
        }
        return REPOSITORY_FIELD_ALIASES.getOrDefault(field.trim(), field.trim());
    }

    private List<AccountPermissionOverride> loadAllMatching(BaseSearchRequest request) {
        List<AccountPermissionOverride> matches = new ArrayList<>();
        int page = 0;
        while (true) {
            BaseSearchRequest batch = copyRequest(request);
            batch.setPage(page);
            batch.setSize(BaseSearchRequest.MAX_PAGE_SIZE);
            Page<AccountPermissionOverride> result = accountPermissionOverrideRepository.findAll(batch);
            matches.addAll(result.getContent());
            if (!result.hasNext()) {
                return matches;
            }
            page++;
        }
    }

    private Page<AccountPermissionOverrideResponse> page(BaseSearchRequest request, List<AccountPermissionOverride> overrides) {
        int page = request == null ? 0 : request.getPage();
        int size = request == null ? 10 : request.getSize();
        int start = Math.min(page * size, overrides.size());
        int end = Math.min(start + size, overrides.size());
        return new PageImpl<>(overrides.subList(start, end).stream().map(this::toResponse).toList(), PageRequest.of(page, size), overrides.size());
    }

    private AccountPermissionOverrideResponse toResponse(AccountPermissionOverride override) {
        return AccountPermissionOverrideResponse.builder()
                .id(override.getId())
                .username(override.getAccount() == null ? null : override.getAccount().getUsername())
                .permissionCode(override.getPermission() == null ? null : override.getPermission().getPermissionCode())
                .permissionName(override.getPermission() == null ? null : override.getPermission().getPermissionName())
                .effect(override.getEffect())
                .reason(override.getReason())
                .expiresAt(override.getExpiresAt())
                .status(override.getStatus())
                .validity(PermissionValiditySupport.validity(override.getRevokedAt(), override.getRevocationReason(),
                        override.getExpiresAt(), override.getPermission(), OffsetDateTime.now()))
                .revocationReason(override.getRevocationReason())
                .revokedAt(override.getRevokedAt())
                .revokedBy(override.getRevokedBy())
                .build();
    }

    private PermissionResponse toPermissionResponse(Permission permission) {
        return PermissionResponse.builder()
                .permissionCode(permission.getPermissionCode())
                .permissionName(permission.getPermissionName())
                .moduleCode(permission.getModule() == null ? null : permission.getModule().getModuleCode())
                .resourceCode(permission.getResource() == null ? null : permission.getResource().getResourceCode())
                .actionCode(permission.getAction() == null ? null : permission.getAction().getActionCode())
                .scopeCode(permission.getScope() == null ? null : permission.getScope().getScopeCode())
                .description(permission.getDescription())
                .isSystem(permission.getIsSystem())
                .displayOrder(permission.getDisplayOrder())
                .status(permission.getStatus())
                .createdAt(permission.getCreatedAt())
                .updatedAt(permission.getUpdatedAt())
                .build();
    }

    private boolean canReadOverride(Authentication authentication, AccountPermissionOverride override) {
        if (authorizationHelper.isSuperAdmin(authentication)) {
            return isValidSuperAdminTarget(override.getAccount());
        }
        return authorizationHelper.canManageAccountPermission(authentication, override.getAccount().getUsername(), override.getPermission());
    }

    private void requireValidSuperAdminTarget(Authentication authentication, Account account) {
        if (authorizationHelper.isSuperAdmin(authentication) && !isValidSuperAdminTarget(account)) {
            throw commonValidationHelper.forbidden("SUPER_ADMIN chỉ được phân quyền riêng cho tài khoản do super-admin tạo");
        }
    }

    // SUPER_ADMIN chỉ phân quyền riêng cho tài khoản do super-admin tạo; tài khoản do các cấp nhỏ hơn tạo không có quyền CRUD.
    private boolean isValidSuperAdminTarget(Account account) {
        return account != null && ManagementOwnership.isSuperAdminOwned(account.getCreatedBy());
    }

    private AccountPermissionOverride createOverride(Account account, Permission permission, String actor) {
        AccountPermissionOverrideCreateRequest createRequest = new AccountPermissionOverrideCreateRequest();
        createRequest.setUsername(account.getUsername());
        createRequest.setPermissionCode(permission.getPermissionCode());
        createRequest.setEffect(PermissionEffect.ALLOW);
        AccountPermissionOverride override = accountPermissionOverrideRepository.create(createRequest, actor);
        override.setAccount(account);
        override.setPermission(permission);
        override.setStatus(true);
        override.setUpdatedAt(OffsetDateTime.now());
        override.setUpdatedBy(actor);
        return override;
    }

    private Set<String> assignedPermissionCodes(String username) {
        Set<String> assignedPermissionCodes = new LinkedHashSet<>(accountPermissionOverrideRepository.findPermissionCodesByUsername(username));
        assignedPermissionCodes.addAll(rolePermissionRepository.findActivePermissionCodesByAccountUsername(username));
        return assignedPermissionCodes;
    }

    private void rejectDenyOfRequiredAccountRoleAssignment(Account account, String permissionCode, PermissionEffect effect) {
        if (effect != PermissionEffect.DENY) {
            return;
        }
        String requiredRoleCode = requiredAdminRoleForAccountRolePermission(permissionCode);
        if (requiredRoleCode == null) {
            return;
        }
        boolean hasRequiredRole = accountRoleRepository.findActiveByUsername(account.getUsername()).stream()
                .anyMatch(accountRole -> accountRole.getRole() != null && requiredRoleCode.equals(accountRole.getRole().getRoleCode()));
        if (hasRequiredRole) {
            throw commonValidationHelper.conflict("Không thể DENY quyền gán vai trò nền tảng của tài khoản admin unit");
        }
    }

    private String requiredAdminRoleForAccountRolePermission(String permissionCode) {
        return switch (permissionCode == null ? "" : permissionCode) {
            case "system.account_role.assign.diocese" -> "ADMIN_DIOCESE";
            case "system.account_role.assign.deanery" -> "ADMIN_DEANERY";
            case "system.account_role.assign.parish" -> "ADMIN_PARISH";
            default -> null;
        };
    }
}
