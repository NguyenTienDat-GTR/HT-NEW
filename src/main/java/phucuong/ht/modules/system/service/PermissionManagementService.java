package phucuong.ht.modules.system.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.request.PermissionCreateRequest;
import phucuong.ht.modules.system.model.request.PermissionUpdateRequest;
import phucuong.ht.modules.system.model.response.PermissionResponse;
import phucuong.ht.modules.system.model.response.PermissionTaxonomyResponse;
import phucuong.ht.modules.system.repository.SystemPermissionRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PermissionManagementService {

    private final SystemPermissionRepository systemPermissionRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final SystemAdminAuthorizationHelper authorizationHelper;

    public PermissionResponse create(PermissionCreateRequest request, Authentication authentication) {
        String moduleCode = normalizeCode(request.getModuleCode(), "Ma module khong duoc de trong");
        String resourceCode = normalizeCode(request.getResourceCode(), "Ma resource khong duoc de trong");
        String actionCode = normalizeCode(request.getActionCode(), "Ma action khong duoc de trong");
        String scopeCode = normalizeCode(request.getScopeCode(), "Ma scope khong duoc de trong");
        String permissionCode = buildPermissionCode(moduleCode, resourceCode, actionCode, scopeCode);

        validateTaxonomy(moduleCode, resourceCode, actionCode, scopeCode);
        if (systemPermissionRepository.findById(permissionCode).isPresent()) {
            throw commonValidationHelper.conflict("Quyen da ton tai: " + permissionCode);
        }

        String actor = commonValidationHelper.requireAuditActor(authentication, "Khong xac dinh duoc nguoi tao quyen");
        PermissionWritePayload payload = new PermissionWritePayload();
        payload.setPermissionCode(permissionCode);
        payload.setPermissionName(normalizeRequiredText(request.getPermissionName(), "Ten quyen khong duoc de trong"));
        payload.setModuleCode(moduleCode);
        payload.setResourceCode(resourceCode);
        payload.setActionCode(actionCode);
        payload.setScopeCode(scopeCode);
        payload.setDescription(commonValidationHelper.normalizeOptionalText(request.getDescription()));
        payload.setIsSystem(Boolean.FALSE);
        payload.setDisplayOrder(request.getDisplayOrder());

        Permission permission = systemPermissionRepository.createPermission(
                payload.getPermissionCode(),
                payload.getPermissionName(),
                payload.getModuleCode(),
                payload.getResourceCode(),
                payload.getActionCode(),
                payload.getScopeCode(),
                payload.getDescription(),
                payload.getIsSystem(),
                payload.getDisplayOrder(),
                actor
        );
        return toResponse(permission);
    }

    public Page<PermissionResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        List<Permission> manageable = loadAllMatching(request).stream()
                .filter(permission -> authorizationHelper.canReadPermission(authentication, permission))
                .toList();
        return page(request, manageable);
    }

    public PermissionResponse findById(String permissionCode, Authentication authentication) {
        Permission permission = systemPermissionRepository.findById(permissionCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Khong tim thay quyen: " + permissionCode));
        authorizationHelper.requirePermissionReadAccess(authentication, permission);
        return toResponse(permission);
    }

    public PermissionTaxonomyResponse taxonomy() {
        return PermissionTaxonomyResponse.builder()
                .modules(systemPermissionRepository.findActiveModules().stream()
                        .map(module -> PermissionTaxonomyResponse.TaxonomyOption.builder()
                                .code(module.getModuleCode())
                                .name(module.getModuleName())
                                .description(module.getDescription())
                                .build())
                        .toList())
                .resources(systemPermissionRepository.findActiveResources().stream()
                        .map(resource -> PermissionTaxonomyResponse.TaxonomyOption.builder()
                                .code(resource.getResourceCode())
                                .name(resource.getResourceName())
                                .parentCode(resource.getModule() == null ? null : resource.getModule().getModuleCode())
                                .description(resource.getDescription())
                                .build())
                        .toList())
                .actions(systemPermissionRepository.findActiveActions().stream()
                        .map(action -> PermissionTaxonomyResponse.TaxonomyOption.builder()
                                .code(action.getActionCode())
                                .name(action.getActionName())
                                .description(action.getDescription())
                                .build())
                        .toList())
                .scopes(systemPermissionRepository.findActiveScopes().stream()
                        .map(scope -> PermissionTaxonomyResponse.TaxonomyOption.builder()
                                .code(scope.getScopeCode())
                                .name(scope.getScopeName())
                                .description(scope.getDescription())
                                .build())
                        .toList())
                .build();
    }

    public PermissionResponse update(String permissionCode, PermissionUpdateRequest request, Authentication authentication) {
        Permission permission = systemPermissionRepository.findById(permissionCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Khong tim thay quyen: " + permissionCode));

        if (request.getPermissionName() != null) {
            permission.setPermissionName(normalizeRequiredText(request.getPermissionName(), "Ten quyen khong duoc de trong"));
        }
        if (request.getDescription() != null) {
            permission.setDescription(commonValidationHelper.normalizeOptionalText(request.getDescription()));
        }
        if (request.getDisplayOrder() != null) {
            permission.setDisplayOrder(request.getDisplayOrder());
        }

        permission.setUpdatedAt(OffsetDateTime.now());
        permission.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Khong xac dinh duoc nguoi cap nhat quyen"));
        return toResponse(permission);
    }

    public void delete(String permissionCode) {
        Permission permission = systemPermissionRepository.findById(permissionCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Khong tim thay quyen: " + permissionCode));
        if (Boolean.TRUE.equals(permission.getIsSystem())) {
            throw commonValidationHelper.forbidden("Khong the xoa quyen he thong");
        }
        systemPermissionRepository.delete(permissionCode);
    }

    public PermissionResponse toggleStatus(String permissionCode, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Khong xac dinh duoc nguoi doi trang thai quyen");
        systemPermissionRepository.toggleStatus(permissionCode, actor);
        return findById(permissionCode, authentication);
    }

    private void validateTaxonomy(String moduleCode, String resourceCode, String actionCode, String scopeCode) {
        if (!systemPermissionRepository.existsModuleCode(moduleCode)) {
            throw commonValidationHelper.notFound("Khong tim thay module: " + moduleCode);
        }
        if (!systemPermissionRepository.existsResourceCodeInModule(resourceCode, moduleCode)) {
            throw commonValidationHelper.notFound("Khong tim thay resource " + resourceCode + " trong module " + moduleCode);
        }
        if (!systemPermissionRepository.existsActionCode(actionCode)) {
            throw commonValidationHelper.notFound("Khong tim thay action: " + actionCode);
        }
        if (!systemPermissionRepository.existsScopeCode(scopeCode)) {
            throw commonValidationHelper.notFound("Khong tim thay scope: " + scopeCode);
        }
    }

    private String buildPermissionCode(String moduleCode, String resourceCode, String actionCode, String scopeCode) {
        return String.join(".", moduleCode, resourceCode, actionCode, scopeCode);
    }

    private String normalizeCode(String value, String message) {
        return normalizeRequiredText(value, message).toLowerCase(Locale.ROOT);
    }

    private String normalizeRequiredText(String value, String message) {
        String normalized = commonValidationHelper.normalizeRequiredText(value);
        if (normalized == null || normalized.isBlank()) {
            throw commonValidationHelper.validationError(message);
        }
        return normalized;
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

    private List<Permission> loadAllMatching(BaseSearchRequest request) {
        List<Permission> matches = new java.util.ArrayList<>();
        int page = 0;
        while (true) {
            BaseSearchRequest batch = copyRequest(request);
            batch.setPage(page);
            batch.setSize(BaseSearchRequest.MAX_PAGE_SIZE);
            Page<Permission> result = systemPermissionRepository.findAll(batch);
            matches.addAll(result.getContent());
            if (!result.hasNext()) {
                return matches;
            }
            page++;
        }
    }

    private Page<PermissionResponse> page(BaseSearchRequest request, List<Permission> permissions) {
        int page = request == null ? 0 : request.getPage();
        int size = request == null ? 10 : request.getSize();
        int start = Math.min(page * size, permissions.size());
        int end = Math.min(start + size, permissions.size());
        return new PageImpl<>(permissions.subList(start, end).stream().map(this::toResponse).toList(), PageRequest.of(page, size), permissions.size());
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

    @Getter
    @Setter
    private static class PermissionWritePayload {
        private String permissionCode;
        private String permissionName;
        private String moduleCode;
        private String resourceCode;
        private String actionCode;
        private String scopeCode;
        private String description;
        private Boolean isSystem;
        private Integer displayOrder;
    }
}
