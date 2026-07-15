package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.entity.RolePermission;
import phucuong.ht.modules.system.model.request.RolePermissionBulkRequest;
import phucuong.ht.modules.system.model.request.RolePermissionCreateRequest;
import phucuong.ht.modules.system.model.request.RolePermissionUpdateRequest;
import phucuong.ht.modules.system.model.response.PermissionResponse;
import phucuong.ht.modules.system.model.response.RolePermissionResponse;
import phucuong.ht.modules.system.repository.RolePermissionRepository;
import phucuong.ht.modules.system.repository.SystemPermissionRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RolePermissionManagementService {

    private final RolePermissionRepository rolePermissionRepository;
    private final RoleRepository roleRepository;
    private final SystemPermissionRepository systemPermissionRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final SystemAdminAuthorizationHelper authorizationHelper;

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

        if (rolePermissionRepository.findByRoleCodeAndPermissionCode(request.getRoleCode(), request.getPermissionCode()).isPresent()) {
            throw commonValidationHelper.conflict("Phân quyền cho vai trò đã tồn tại");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo phân quyền cho vai trò");
        RolePermission rolePermission = rolePermissionRepository.create(request, actor);
        rolePermission.setAssignedBy(actor);
        rolePermission.setAssignedAt(request.getAssignedAt() == null ? OffsetDateTime.now() : request.getAssignedAt());
        return toResponse(rolePermission);
    }

    public List<RolePermissionResponse> bulkAssign(RolePermissionBulkRequest request, Authentication authentication) {
        Role role = roleRepository.findById(request.getRoleCode())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò: " + request.getRoleCode()));
        if (!Boolean.TRUE.equals(role.getStatus())) {
            throw commonValidationHelper.conflict("Không thể gán quyền cho vai trò đang ngừng hoạt động");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo phân quyền cho vai trò");
        OffsetDateTime assignedAt = request.getAssignedAt() == null ? OffsetDateTime.now() : request.getAssignedAt();
        List<RolePermissionResponse> responses = new ArrayList<>();

        for (String permissionCode : new LinkedHashSet<>(request.getPermissionCodes())) {
            Permission permission = systemPermissionRepository.findById(permissionCode)
                    .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy quyền: " + permissionCode));
            if (!Boolean.TRUE.equals(permission.getStatus())) {
                throw commonValidationHelper.conflict("Không thể gán quyền đang ngừng hoạt động: " + permissionCode);
            }
            authorizationHelper.requireRolePermissionWriteAccess(authentication, role, permission, SystemAdminAuthorizationHelper.ROLE_PERMISSION_ASSIGN_DIOCESE);

            RolePermission rolePermission = rolePermissionRepository.findByRoleCodeAndPermissionCode(request.getRoleCode(), permissionCode)
                    .orElseGet(() -> createRolePermission(role, permission, actor, assignedAt));
            rolePermission.setEffect(request.getEffect());
            rolePermission.setConditions(commonValidationHelper.normalizeOptionalText(request.getConditions()));
            rolePermission.setExpiresAt(request.getExpiresAt());
            rolePermission.setStatus(true);
            rolePermission.setUpdatedBy(actor);
            responses.add(toResponse(rolePermission));
        }

        return responses;
    }

    public Page<RolePermissionResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        List<RolePermission> manageable = loadAllMatching(request).stream()
                .filter(rolePermission -> authorizationHelper.canReadRolePermission(
                        authentication, rolePermission.getRole(), rolePermission.getPermission()))
                .toList();
        return page(request, manageable);
    }

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

    public RolePermissionResponse findById(String id, Authentication authentication) {
        RolePermission rolePermission = rolePermissionRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy phân quyền cho vai trò: " + id));
        authorizationHelper.requireRolePermissionReadAccess(authentication, rolePermission.getRole(), rolePermission.getPermission());
        return toResponse(rolePermission);
    }

    public RolePermissionResponse update(String id, RolePermissionUpdateRequest request, Authentication authentication) {
        RolePermission rolePermission = rolePermissionRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy phân quyền cho vai trò: " + id));
        authorizationHelper.requireRolePermissionWriteAccess(
                authentication,
                rolePermission.getRole(),
                rolePermission.getPermission(),
                SystemAdminAuthorizationHelper.ROLE_PERMISSION_ASSIGN_DIOCESE
        );

        if (request.getEffect() != null) {
            rolePermission.setEffect(request.getEffect());
        }
        if (request.getConditions() != null) {
            rolePermission.setConditions(commonValidationHelper.normalizeOptionalText(request.getConditions()));
        }
        if (request.getExpiresAt() != null) {
            rolePermission.setExpiresAt(request.getExpiresAt());
        }
        rolePermission.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật phân quyền cho vai trò"));
        return toResponse(rolePermission);
    }

    public void delete(String id, Authentication authentication) {
        RolePermission rolePermission = rolePermissionRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy phân quyền cho vai trò: " + id));
        authorizationHelper.requireRolePermissionWriteAccess(
                authentication,
                rolePermission.getRole(),
                rolePermission.getPermission(),
                SystemAdminAuthorizationHelper.ROLE_PERMISSION_REVOKE_DIOCESE
        );
        rolePermissionRepository.delete(id);
    }

    public RolePermissionResponse toggleStatus(String id, StatusToggleRequest request, Authentication authentication) {
        RolePermission rolePermission = rolePermissionRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy phân quyền cho vai trò: " + id));
        authorizationHelper.requireRolePermissionWriteAccess(
                authentication,
                rolePermission.getRole(),
                rolePermission.getPermission(),
                SystemAdminAuthorizationHelper.ROLE_PERMISSION_ASSIGN_DIOCESE
        );
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái phân quyền cho vai trò");
        rolePermission.setStatus(true);
        rolePermission.setUpdatedBy(actor);
        return toResponse(rolePermission);
    }

    private BaseSearchRequest copyRequest(BaseSearchRequest request) {
        BaseSearchRequest source = request == null ? new BaseSearchRequest() : request;
        BaseSearchRequest copy = new BaseSearchRequest();
        copy.setPage(source.getPage());
        copy.setSize(source.getSize());
        copy.setSearch(source.getSearch());
        copy.setSearchFields(source.getSearchFields() == null ? List.of() : List.copyOf(source.getSearchFields()));
        copy.setFilters(source.getFilters() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getFilters()));
        copy.setStatus(source.getStatus());
        copy.setSortBy(source.getSortBy());
        copy.setSortDirection(source.getSortDirection());
        return copy;
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

    private Page<RolePermissionResponse> page(BaseSearchRequest request, List<RolePermission> rolePermissions) {
        int page = request == null ? 0 : request.getPage();
        int size = request == null ? 10 : request.getSize();
        int start = Math.min(page * size, rolePermissions.size());
        int end = Math.min(start + size, rolePermissions.size());
        return new PageImpl<>(rolePermissions.subList(start, end).stream().map(this::toResponse).toList(), PageRequest.of(page, size), rolePermissions.size());
    }

    private RolePermissionResponse toResponse(RolePermission rolePermission) {
        return RolePermissionResponse.builder()
                .id(rolePermission.getId())
                .roleCode(rolePermission.getRole() == null ? null : rolePermission.getRole().getRoleCode())
                .permissionCode(rolePermission.getPermission() == null ? null : rolePermission.getPermission().getPermissionCode())
                .effect(rolePermission.getEffect())
                .conditions(rolePermission.getConditions())
                .assignedBy(rolePermission.getAssignedBy())
                .assignedAt(rolePermission.getAssignedAt())
                .expiresAt(rolePermission.getExpiresAt())
                .status(rolePermission.getStatus())
                .build();
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
                .updatedAt(permission.getUpdatedAt())
                .build();
    }

    private RolePermission createRolePermission(Role role, Permission permission, String actor, OffsetDateTime assignedAt) {
        RolePermissionCreateRequest createRequest = new RolePermissionCreateRequest();
        createRequest.setRoleCode(role.getRoleCode());
        createRequest.setPermissionCode(permission.getPermissionCode());
        createRequest.setEffect(phucuong.ht.modules.system.model.enums.PermissionEffect.ALLOW);
        createRequest.setAssignedAt(assignedAt);
        RolePermission rolePermission = rolePermissionRepository.create(createRequest, actor);
        rolePermission.setRole(role);
        rolePermission.setPermission(permission);
        rolePermission.setAssignedBy(actor);
        rolePermission.setAssignedAt(assignedAt);
        rolePermission.setStatus(true);
        return rolePermission;
    }
}
