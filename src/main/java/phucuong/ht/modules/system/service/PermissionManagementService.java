package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.request.PermissionCreateRequest;
import phucuong.ht.modules.system.model.request.PermissionUpdateRequest;
import phucuong.ht.modules.system.model.response.PermissionResponse;
import phucuong.ht.modules.system.repository.SystemPermissionRepository;

import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionManagementService {

    private final SystemPermissionRepository systemPermissionRepository;
    private final CommonValidationHelper commonValidationHelper;

    public PermissionResponse create(PermissionCreateRequest request, Authentication authentication) {
        if (systemPermissionRepository.findById(request.getPermissionCode()).isPresent()) {
            throw commonValidationHelper.conflict("Permission already exists: " + request.getPermissionCode());
        }
        String actor = commonValidationHelper.requireAuditActor(authentication, "Unable to resolve permission creator");
        Permission permission = systemPermissionRepository.create(request, actor);
        return toResponse(permission);
    }

    public Page<PermissionResponse> findAll(BaseSearchRequest request) {
        Page<Permission> page = systemPermissionRepository.findAll(copyRequest(request));
        return new PageImpl<>(page.getContent().stream().map(this::toResponse).toList(), page.getPageable(), page.getTotalElements());
    }

    public PermissionResponse findById(String permissionCode) {
        return toResponse(systemPermissionRepository.findById(permissionCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Permission not found: " + permissionCode)));
    }

    public PermissionResponse update(String permissionCode, PermissionUpdateRequest request, Authentication authentication) {
        Permission permission = systemPermissionRepository.findById(permissionCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Permission not found: " + permissionCode));
        if (request.getPermissionName() != null) {
            permission.setPermissionName(commonValidationHelper.normalizeRequiredText(request.getPermissionName()));
        }
        if (request.getModuleCode() != null) {
            systemPermissionRepository.update(permissionCode, request, commonValidationHelper.requireAuditActor(authentication, "Unable to resolve permission updater"));
            permission = systemPermissionRepository.getRequired(permissionCode);
            return toResponse(permission);
        }
        if (request.getResourceCode() != null || request.getActionCode() != null || request.getScopeCode() != null) {
            systemPermissionRepository.update(permissionCode, request, commonValidationHelper.requireAuditActor(authentication, "Unable to resolve permission updater"));
            permission = systemPermissionRepository.getRequired(permissionCode);
            return toResponse(permission);
        }
        if (request.getDescription() != null) {
            permission.setDescription(commonValidationHelper.normalizeOptionalText(request.getDescription()));
        }
        if (request.getIsSystem() != null) {
            permission.setIsSystem(request.getIsSystem());
        }
        if (request.getDisplayOrder() != null) {
            permission.setDisplayOrder(request.getDisplayOrder());
        }
        permission.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Unable to resolve permission updater"));
        return toResponse(permission);
    }

    public void delete(String permissionCode) {
        systemPermissionRepository.delete(permissionCode);
    }

    public PermissionResponse toggleStatus(String permissionCode, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Unable to resolve permission status updater");
        systemPermissionRepository.toggleStatus(permissionCode, actor);
        return findById(permissionCode);
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

    private PermissionResponse toResponse(Permission permission) {
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
}
