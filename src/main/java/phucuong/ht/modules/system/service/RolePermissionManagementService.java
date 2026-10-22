package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.entity.RolePermission;
import phucuong.ht.modules.system.model.enums.PermissionRevocationReason;
import phucuong.ht.modules.system.model.request.RolePermissionBulkRequest;
import phucuong.ht.modules.system.model.request.RolePermissionRevokeRequest;
import phucuong.ht.modules.system.model.request.RolePermissionCreateRequest;
import phucuong.ht.modules.system.model.request.RolePermissionUpdateRequest;
import phucuong.ht.modules.system.model.response.PermissionResponse;
import phucuong.ht.modules.system.model.response.RoleResponse;
import phucuong.ht.modules.system.model.response.RolePermissionResponse;
import phucuong.ht.modules.system.repository.RolePermissionRepository;
import phucuong.ht.modules.system.repository.SystemPermissionRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RolePermissionManagementService {

    private static final Map<String, String> REPOSITORY_FIELD_ALIASES = Map.of(
            "roleCode", "role.roleCode",
            "roleName", "role.roleName",
            "permissionCode", "permission.permissionCode",
            "permissionName", "permission.permissionName"
    );
    private static final Set<String> REPOSITORY_FIELDS = Set.of(
            "id",
            "role.roleCode",
            "role.roleName",
            "permission.permissionCode",
            "permission.permissionName",
            "permission.module.moduleCode",
            "permission.resource.resourceCode",
            "permission.action.actionCode",
            "permission.scope.scopeCode",
            "conditions",
            "assignedBy",
            "assignedAt",
            "expiresAt",
            "status",
            "createdAt",
            "createdBy",
            "updatedAt",
            "updatedBy"
    );

    private final RolePermissionRepository rolePermissionRepository;
    private final RoleRepository roleRepository;
    private final SystemPermissionRepository systemPermissionRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final SystemAdminAuthorizationHelper authorizationHelper;
    private final phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;
    @Autowired(required = false)
    private RbacAuthRefreshNotifier authRefreshNotifier;
    @Autowired(required = false)
    private phucuong.ht.modules.notification.service.PermissionGrantNotifier permissionGrantNotifier;

    @Transactional
    public RolePermissionResponse create(RolePermissionCreateRequest request, Authentication authentication) {
        Role role = roleRepository.findById(request.getRoleCode())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò: " + request.getRoleCode()));
        Permission permission = systemPermissionRepository.findById(request.getPermissionCode())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy quyền: " + request.getPermissionCode()));
        if (!Boolean.TRUE.equals(role.getStatus())) {
            throw commonValidationHelper.conflict("Không thể gán quyền cho vai trò đang ngừng hoạt động");
        }
        if (!Boolean.TRUE.equals(permission.getStatus())) {
            throw commonValidationHelper.conflict("Không thể gán quyền đang ngừng hoạt động");
        }

        authorizationHelper.requireRolePermissionWriteAccess(authentication, role, permission, SystemAdminAuthorizationHelper.ROLE_PERMISSION_ASSIGN_DIOCESE);

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo phân quyền cho vai trò");
        RolePermission existing = rolePermissionRepository.findByRoleCodeAndPermissionCode(request.getRoleCode(), request.getPermissionCode()).orElse(null);
        if (existing != null) {
            if (!PermissionValiditySupport.isPermanentlyRevoked(existing.getRevokedAt(), existing.getExpiresAt(), OffsetDateTime.now())) {
                throw commonValidationHelper.conflict("Phân quyền cho vai trò đã tồn tại");
            }
            // Quyền đã thu hồi/hết hạn không bật lại được: phân quyền mới thay thế bản ghi cũ.
            OffsetDateTime assignedAt = request.getAssignedAt() == null ? OffsetDateTime.now() : request.getAssignedAt();
            existing.setConditions(commonValidationHelper.normalizeOptionalText(request.getConditions()));
            existing.setExpiresAt(request.getExpiresAt());
            reissue(existing, actor, assignedAt);
            notifyRolePermissionChanged(request.getRoleCode(), "ROLE_PERMISSION_CREATED");
            notifyRoleGranted(role, List.of(permission), actor);
            return toResponse(existing);
        }

        RolePermission rolePermission = rolePermissionRepository.create(request, actor);
        rolePermission.setAssignedBy(actor);
        rolePermission.setAssignedAt(request.getAssignedAt() == null ? OffsetDateTime.now() : request.getAssignedAt());
        notifyRolePermissionChanged(request.getRoleCode(), "ROLE_PERMISSION_CREATED");
        notifyRoleGranted(role, List.of(permission), actor);
        return toResponse(rolePermission);
    }

    @Transactional
    public List<RolePermissionResponse> bulkAssign(RolePermissionBulkRequest request, Authentication authentication) {
        Role role = roleRepository.findById(request.getRoleCode())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò: " + request.getRoleCode()));
        if (!Boolean.TRUE.equals(role.getStatus())) {
            throw commonValidationHelper.conflict("Không thể gán quyền cho vai trò đang ngừng hoạt động");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo phân quyền cho vai trò");
        OffsetDateTime assignedAt = request.getAssignedAt() == null ? OffsetDateTime.now() : request.getAssignedAt();
        OffsetDateTime now = OffsetDateTime.now();
        List<RolePermissionResponse> responses = new ArrayList<>();
        List<Permission> grantedPermissions = new ArrayList<>();

        for (String permissionCode : new LinkedHashSet<>(request.getPermissionCodes())) {
            Permission permission = systemPermissionRepository.findById(permissionCode)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy quyền: " + permissionCode));
            if (!Boolean.TRUE.equals(permission.getStatus())) {
                throw commonValidationHelper.conflict("Không thể gán quyền đang ngừng hoạt động: " + permissionCode);
            }
            authorizationHelper.requireRolePermissionWriteAccess(authentication, role, permission, SystemAdminAuthorizationHelper.ROLE_PERMISSION_ASSIGN_DIOCESE);

            RolePermission rolePermission = rolePermissionRepository.findByRoleCodeAndPermissionCode(request.getRoleCode(), permissionCode)
                    .orElseGet(() -> createRolePermission(role, permission, actor, assignedAt));
            if (PermissionValiditySupport.isPermanentlyRevoked(rolePermission.getRevokedAt(), rolePermission.getExpiresAt(), now)) {
                reissue(rolePermission, actor, assignedAt);
            }
            rolePermission.setConditions(commonValidationHelper.normalizeOptionalText(request.getConditions()));
            rolePermission.setExpiresAt(request.getExpiresAt());
            rolePermission.setStatus(true);
            rolePermission.setUpdatedAt(now);
            rolePermission.setUpdatedBy(actor);
            responses.add(toResponse(rolePermission));
            grantedPermissions.add(permission);
        }

        notifyRolePermissionChanged(request.getRoleCode(), "ROLE_PERMISSION_BULK_ASSIGNED");
        notifyRoleGranted(role, grantedPermissions, actor);
        return responses;
    }

    /** Thu hồi nhiều quyền đã gán cho vai trò: đánh dấu thu hồi vĩnh viễn, giữ lại bản ghi (muốn cấp lại phải phân quyền mới). */
    @Transactional
    public int bulkRevoke(RolePermissionRevokeRequest request, Authentication authentication) {
        Role role = roleRepository.findById(request.getRoleCode())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò: " + request.getRoleCode()));
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người thu hồi phân quyền của vai trò");
        int revoked = 0;
        for (String permissionCode : new LinkedHashSet<>(request.getPermissionCodes())) {
            RolePermission rolePermission = rolePermissionRepository.findByRoleCodeAndPermissionCode(request.getRoleCode(), permissionCode)
                    .orElseThrow(() -> commonValidationHelper.notFound("Vai trò chưa được gán quyền: " + permissionCode));
            authorizationHelper.requireRolePermissionWriteAccess(authentication, role, rolePermission.getPermission(), SystemAdminAuthorizationHelper.ROLE_PERMISSION_REVOKE_DIOCESE);
            authorizationHelper.requireRevocableOrigin(authentication, rolePermission.getAssignedBy(), rolePermission.getCreatedBy());
            authorizationHelper.requireNotOwnRole(authentication, role);
            OffsetDateTime now = OffsetDateTime.now();
            if (PermissionValiditySupport.isPermanentlyRevoked(rolePermission.getRevokedAt(), rolePermission.getExpiresAt(), now)) {
                throw commonValidationHelper.conflict("Quyền đã bị thu hồi hoặc hết hạn: " + permissionCode);
            }
            rolePermission.setRevokedAt(now);
            rolePermission.setRevokedBy(actor);
            rolePermission.setRevocationReason(PermissionRevocationReason.MANUAL);
            rolePermission.setUpdatedAt(now);
            rolePermission.setUpdatedBy(actor);
            revoked++;
        }
        notifyRolePermissionChanged(request.getRoleCode(), "ROLE_PERMISSION_BULK_REVOKED");
        return revoked;
    }

    /** Quyền đã gán cho vai trò mà tài khoản hiện tại được phép thu hồi. */
    @Transactional(readOnly = true)
    public List<PermissionResponse> findRevocablePermissions(String roleCode, Authentication authentication) {
        Role role = roleRepository.findById(roleCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò: " + roleCode));
        return rolePermissionRepository.findPermissionCodesByRoleCode(roleCode).stream()
                .map(systemPermissionRepository::findById)
                .flatMap(java.util.Optional::stream)
                .filter(permission -> canRevokePermission(authentication, role, permission))
                .filter(permission -> rolePermissionRepository.findByRoleCodeAndPermissionCode(roleCode, permission.getPermissionCode())
                        .map(assignment -> authorizationHelper.canRevokeRolePermission(authentication, role, permission, assignment.getAssignedBy(), assignment.getCreatedBy()))
                        .orElse(false))
                .map(this::toPermissionResponse)
                .toList();
    }

    private boolean canRevokePermission(Authentication authentication, Role role, Permission permission) {
        try {
            authorizationHelper.requireRolePermissionWriteAccess(authentication, role, permission, SystemAdminAuthorizationHelper.ROLE_PERMISSION_REVOKE_DIOCESE);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public Page<RolePermissionResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        List<RolePermission> manageable = loadAllMatching(request).stream()
                .filter(rolePermission -> authorizationHelper.canReadRolePermission(
                        authentication, rolePermission.getRole(), rolePermission.getPermission()))
                .toList();
        return page(request, manageable, authentication);
    }

    /** Vai trò mà người dùng hiện tại được phép phân quyền / thu hồi quyền (action = assign | revoke); đã loại vai trò cấp cao hơn. */
    @Transactional(readOnly = true)
    public List<RoleResponse> findManageableRoles(String action, Authentication authentication) {
        String authority = "revoke".equalsIgnoreCase(action)
                ? SystemAdminAuthorizationHelper.ROLE_PERMISSION_REVOKE_DIOCESE
                : SystemAdminAuthorizationHelper.ROLE_PERMISSION_ASSIGN_DIOCESE;
        boolean assign = !"revoke".equalsIgnoreCase(action);
        return roleRepository.findAll().stream()
                .filter(role -> !assign || Boolean.TRUE.equals(role.getStatus()))
                .filter(role -> authorizationHelper.canManageRolePermissionRole(authentication, role, authority))
                .sorted(java.util.Comparator.comparing(Role::getDisplayOrder, java.util.Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Role::getRoleCode))
                .map(role -> RoleResponse.builder()
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
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> findAssignablePermissions(String roleCode, Authentication authentication) {
        Role role = roleRepository.findById(roleCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò: " + roleCode));
        if (!Boolean.TRUE.equals(role.getStatus())) {
            throw commonValidationHelper.conflict("Không thể gán quyền cho vai trò đang ngừng hoạt động");
        }
        Set<String> assignedPermissionCodes = Set.copyOf(rolePermissionRepository.findPermissionCodesByRoleCode(roleCode));
        return systemPermissionRepository.findActivePermissions().stream()
                .filter(permission -> !assignedPermissionCodes.contains(permission.getPermissionCode()))
                .filter(permission -> canAssignPermission(authentication, role, permission))
                .map(this::toPermissionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RolePermissionResponse findById(String id, Authentication authentication) {
        RolePermission rolePermission = rolePermissionRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy phân quyền cho vai trò: " + id));
        authorizationHelper.requireRolePermissionReadAccess(authentication, rolePermission.getRole(), rolePermission.getPermission());
        return toResponse(rolePermission, authentication);
    }

    @Transactional
    public RolePermissionResponse update(String id, RolePermissionUpdateRequest request, Authentication authentication) {
        RolePermission rolePermission = rolePermissionRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy phân quyền cho vai trò: " + id));
        authorizationHelper.requireRolePermissionWriteAccess(
                authentication,
                rolePermission.getRole(),
                rolePermission.getPermission(),
                SystemAdminAuthorizationHelper.ROLE_PERMISSION_ASSIGN_DIOCESE
        );
        requireNotRevoked(rolePermission);

        if (request.getConditions() != null) {
            rolePermission.setConditions(commonValidationHelper.normalizeOptionalText(request.getConditions()));
        }
        if (request.getExpiresAt() != null) {
            rolePermission.setExpiresAt(request.getExpiresAt());
        }
        rolePermission.setUpdatedAt(OffsetDateTime.now());
        rolePermission.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật phân quyền cho vai trò"));
        notifyRolePermissionChanged(rolePermission.getRole().getRoleCode(), "ROLE_PERMISSION_UPDATED");
        return toResponse(rolePermission);
    }


    private void requireNotRevoked(RolePermission rolePermission) {
        if (PermissionValiditySupport.isPermanentlyRevoked(rolePermission.getRevokedAt(), rolePermission.getExpiresAt(), OffsetDateTime.now())) {
            throw commonValidationHelper.conflict("Phân quyền đã bị thu hồi hoặc hết hạn, không thể thay đổi; hãy tạo phân quyền mới");
        }
    }

    private void reissue(RolePermission rolePermission, String actor, OffsetDateTime assignedAt) {
        rolePermission.setRevokedAt(null);
        rolePermission.setRevokedBy(null);
        rolePermission.setRevocationReason(null);
        rolePermission.setAssignedBy(actor);
        rolePermission.setAssignedAt(assignedAt);
        rolePermission.setStatus(true);
        rolePermission.setUpdatedAt(OffsetDateTime.now());
        rolePermission.setUpdatedBy(actor);
    }

    // Một request phân quyền gửi đúng một thông báo cho các tài khoản đang giữ vai trò, kèm danh sách quyền vừa phân.
    private void notifyRoleGranted(Role role, List<Permission> permissions, String actor) {
        if (permissionGrantNotifier != null) {
            permissionGrantNotifier.notifyRoleGranted(role, permissions, actor);
        }
    }

    private void notifyRolePermissionChanged(String roleCode, String reason) {
        if (authRefreshNotifier != null) {
            authRefreshNotifier.notifyRoleChanged(roleCode, reason);
        }
    }





    private BaseSearchRequest copyRequest(BaseSearchRequest request) {
        BaseSearchRequest source = request == null ? new BaseSearchRequest() : request;
        BaseSearchRequest copy = new BaseSearchRequest();
        copy.setPage(source.getPage());
        copy.setSize(source.getSize());
        copy.setSearch(source.getSearch());
        copy.setSearchFields(normalizeFieldPaths(source.getSearchFields()));
        copy.setFilters(normalizeFilters(source.getFilters()));
        copy.setStatus(source.getStatus());
        copy.setSortBy(toRepositoryField(source.getSortBy()));
        copy.setSortDirection(source.getSortDirection());
        return copy;
    }

    private List<String> normalizeFieldPaths(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }
        return fields.stream()
                .map(this::toRepositoryField)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private LinkedHashMap<String, Object> normalizeFilters(java.util.Map<String, Object> filters) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        if (filters == null || filters.isEmpty()) {
            return normalized;
        }
        filters.forEach((key, value) -> {
            String repositoryField = toRepositoryField(key);
            if (repositoryField != null) {
                normalized.put(repositoryField, value);
            }
        });
        return normalized;
    }

    private String toRepositoryField(String field) {
        if (field == null) {
            return null;
        }
        String normalized = field.trim();
        String repositoryField = REPOSITORY_FIELD_ALIASES.getOrDefault(normalized, normalized);
        return REPOSITORY_FIELDS.contains(repositoryField) ? repositoryField : null;
    }

    private List<RolePermission> loadAllMatching(BaseSearchRequest request) {
        List<RolePermission> matches = new java.util.ArrayList<>();
        int page = 0;
        while (true) {
            BaseSearchRequest batch = copyRequest(request);
            batch.setPage(page);
            batch.setSize(BaseSearchRequest.MAX_PAGE_SIZE);
            Page<RolePermission> result = rolePermissionRepository.findAll(batch);
            matches.addAll(result.getContent());
            if (!result.hasNext()) {
                return matches;
            }
            page++;
        }
    }

    private Page<RolePermissionResponse> page(BaseSearchRequest request, List<RolePermission> rolePermissions, Authentication authentication) {
        int page = request == null ? 0 : request.getPage();
        int size = request == null ? 10 : request.getSize();
        int start = Math.min(page * size, rolePermissions.size());
        int end = Math.min(start + size, rolePermissions.size());
        return new PageImpl<>(rolePermissions.subList(start, end).stream().map(item -> toResponse(item, authentication)).toList(), PageRequest.of(page, size), rolePermissions.size());
    }

    private RolePermissionResponse toResponse(RolePermission rolePermission) {
        return responseBuilder(rolePermission).build();
    }

    private RolePermissionResponse toResponse(RolePermission rolePermission, Authentication authentication) {
        boolean revocable = !PermissionValiditySupport.isPermanentlyRevoked(rolePermission.getRevokedAt(), rolePermission.getExpiresAt(), OffsetDateTime.now())
                && authorizationHelper.canRevokeRolePermission(authentication, rolePermission.getRole(), rolePermission.getPermission(),
                rolePermission.getAssignedBy(), rolePermission.getCreatedBy());
        return responseBuilder(rolePermission).revocable(revocable).build();
    }

    private RolePermissionResponse.RolePermissionResponseBuilder responseBuilder(RolePermission rolePermission) {
        return RolePermissionResponse.builder()
                .id(rolePermission.getId())
                .roleCode(rolePermission.getRole() == null ? null : rolePermission.getRole().getRoleCode())
                .roleName(rolePermission.getRole() == null ? null : rolePermission.getRole().getRoleName())
                .permissionCode(rolePermission.getPermission() == null ? null : rolePermission.getPermission().getPermissionCode())
                .permissionName(rolePermission.getPermission() == null ? null : rolePermission.getPermission().getPermissionName())
                .conditions(rolePermission.getConditions())
                .assignedBy(rolePermission.getAssignedBy())
                .assignedByName(auditActorNameResolver.resolve(rolePermission.getAssignedBy()))
                .assignedAt(rolePermission.getAssignedAt())
                .expiresAt(rolePermission.getExpiresAt())
                .status(rolePermission.getStatus())
                .validity(PermissionValiditySupport.validity(rolePermission.getRevokedAt(), rolePermission.getRevocationReason(),
                        rolePermission.getExpiresAt(), rolePermission.getPermission(), OffsetDateTime.now()))
                .revocationReason(rolePermission.getRevocationReason())
                .revokedAt(rolePermission.getRevokedAt())
                .revokedBy(rolePermission.getRevokedBy())
                .revokedByName(auditActorNameResolver.resolve(rolePermission.getRevokedBy()))
                .createdAt(rolePermission.getCreatedAt())
                .createdBy(rolePermission.getCreatedBy())
                .createdByName(auditActorNameResolver.resolve(rolePermission.getCreatedBy()))
                .updatedAt(rolePermission.getUpdatedAt())
                .updatedBy(rolePermission.getUpdatedBy())
                .updatedByName(auditActorNameResolver.resolve(rolePermission.getUpdatedBy()));
    }

    private boolean canAssignPermission(Authentication authentication, Role role, Permission permission) {
        try {
            authorizationHelper.requireRolePermissionWriteAccess(authentication, role, permission, SystemAdminAuthorizationHelper.ROLE_PERMISSION_ASSIGN_DIOCESE);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
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
                .createdBy(permission.getCreatedBy())
                .createdByName(auditActorNameResolver.resolve(permission.getCreatedBy()))
                .updatedAt(permission.getUpdatedAt())
                .updatedBy(permission.getUpdatedBy())
                .updatedByName(auditActorNameResolver.resolve(permission.getUpdatedBy()))
                .build();
    }

    private RolePermission createRolePermission(Role role, Permission permission, String actor, OffsetDateTime assignedAt) {
        RolePermissionCreateRequest createRequest = new RolePermissionCreateRequest();
        createRequest.setRoleCode(role.getRoleCode());
        createRequest.setPermissionCode(permission.getPermissionCode());
        createRequest.setAssignedAt(assignedAt);
        RolePermission rolePermission = rolePermissionRepository.create(createRequest, actor);
        rolePermission.setRole(role);
        rolePermission.setPermission(permission);
        rolePermission.setAssignedBy(actor);
        rolePermission.setAssignedAt(assignedAt);
        rolePermission.setStatus(true);
        rolePermission.setUpdatedAt(OffsetDateTime.now());
        rolePermission.setUpdatedBy(actor);
        return rolePermission;
    }
}
