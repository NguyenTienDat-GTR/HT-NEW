package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.entity.RolePermission;
import phucuong.ht.modules.system.model.request.RolePermissionCreateRequest;
import phucuong.ht.modules.system.model.request.RolePermissionUpdateRequest;
import phucuong.ht.modules.system.model.response.RolePermissionResponse;
import phucuong.ht.modules.system.repository.RolePermissionRepository;
import phucuong.ht.modules.system.repository.SystemPermissionRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;

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
                .orElseThrow(() -> commonValidationHelper.notFound("Role not found: " + request.getRoleCode()));
        Permission permission = systemPermissionRepository.findById(request.getPermissionCode())
                .orElseThrow(() -> commonValidationHelper.notFound("Permission not found: " + request.getPermissionCode()));
        if (!Boolean.TRUE.equals(role.getStatus())) {
            throw commonValidationHelper.conflict("Cannot assign permission to inactive role");
        }
        if (!Boolean.TRUE.equals(permission.getStatus())) {
            throw commonValidationHelper.conflict("Cannot assign inactive permission");
        }

        authorizationHelper.requireRolePermissionWriteAccess(authentication, role, permission, SystemAdminAuthorizationHelper.ROLE_PERMISSION_ASSIGN_DIOCESE);

        if (rolePermissionRepository.findByRoleCodeAndPermissionCode(request.getRoleCode(), request.getPermissionCode()).isPresent()) {
            throw commonValidationHelper.conflict("Role permission already exists");
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Unable to resolve role-permission creator");
        RolePermission rolePermission = rolePermissionRepository.create(request, actor);
        rolePermission.setAssignedBy(actor);
        rolePermission.setAssignedAt(request.getAssignedAt() == null ? OffsetDateTime.now() : request.getAssignedAt());
        return toResponse(rolePermission);
    }

    public Page<RolePermissionResponse> findAll(BaseSearchRequest request) {
        Page<RolePermission> page = rolePermissionRepository.findAll(copyRequest(request));
        return new PageImpl<>(page.getContent().stream().map(this::toResponse).toList(), page.getPageable(), page.getTotalElements());
    }

    public RolePermissionResponse findById(String id) {
        return toResponse(rolePermissionRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Role permission not found: " + id)));
    }

    public RolePermissionResponse update(String id, RolePermissionUpdateRequest request, Authentication authentication) {
        RolePermission rolePermission = rolePermissionRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Role permission not found: " + id));
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
        rolePermission.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Unable to resolve role-permission updater"));
        return toResponse(rolePermission);
    }

    public void delete(String id, Authentication authentication) {
        RolePermission rolePermission = rolePermissionRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Role permission not found: " + id));
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
                .orElseThrow(() -> commonValidationHelper.notFound("Role permission not found: " + id));
        authorizationHelper.requireRolePermissionWriteAccess(
                authentication,
                rolePermission.getRole(),
                rolePermission.getPermission(),
                SystemAdminAuthorizationHelper.ROLE_PERMISSION_ASSIGN_DIOCESE
        );
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Unable to resolve role-permission status updater");
        rolePermissionRepository.toggleStatus(id, actor);
        return findById(id);
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
}
