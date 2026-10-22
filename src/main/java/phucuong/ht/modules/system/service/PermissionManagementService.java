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
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.notification.service.ToggleNotificationDispatcher;
import phucuong.ht.modules.notification.service.ToggleNotificationRecipientResolver;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.request.PermissionUpdateRequest;
import phucuong.ht.modules.system.model.response.PermissionResponse;
import phucuong.ht.modules.system.model.response.PermissionTaxonomyResponse;
import phucuong.ht.modules.system.repository.SystemPermissionRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionManagementService {

    private static final java.util.Map<String, String> REPOSITORY_FIELD_ALIASES = java.util.Map.of(
            "moduleCode", "module.moduleCode",
            "resourceCode", "resource.resourceCode",
            "actionCode", "action.actionCode",
            "scopeCode", "scope.scopeCode"
    );

    private final SystemPermissionRepository systemPermissionRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final SystemAdminAuthorizationHelper authorizationHelper;
    private final phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;
    @Autowired(required = false)
    private ToggleNotificationRecipientResolver toggleNotificationRecipientResolver;
    @Autowired(required = false)
    private ToggleNotificationDispatcher toggleNotificationDispatcher;
    @Autowired(required = false)
    private RbacAuthRefreshNotifier authRefreshNotifier;

    public Page<PermissionResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest repositoryRequest = copyRequest(request);
        List<Permission> manageable = loadAllMatching(repositoryRequest).stream()
                .filter(permission -> authorizationHelper.canReadPermission(authentication, permission))
                .toList();
        return page(request, manageable);
    }

    public PermissionResponse findById(String permissionCode, Authentication authentication) {
        Permission permission = systemPermissionRepository.findById(permissionCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy quyền: " + permissionCode));
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

    @Transactional
    public PermissionResponse update(String permissionCode, PermissionUpdateRequest request, Authentication authentication) {
        Permission permission = systemPermissionRepository.findById(permissionCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy quyền: " + permissionCode));

        if (request.getPermissionName() != null) {
            permission.setPermissionName(normalizeRequiredText(request.getPermissionName(), "Tên quyền không được để trống"));
        }
        if (request.getDescription() != null) {
            permission.setDescription(commonValidationHelper.normalizeOptionalText(request.getDescription()));
        }
        if (request.getDisplayOrder() != null) {
            permission.setDisplayOrder(request.getDisplayOrder());
        }

        permission.setUpdatedAt(OffsetDateTime.now());
        permission.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật quyền"));
        Permission saved = systemPermissionRepository.save(permission);
        notifyPermissionChanged(permissionCode, "PERMISSION_UPDATED");
        return toResponse(saved);
    }

    @Transactional
    public PermissionResponse toggleStatus(String permissionCode, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái quyền");
        Set<String> recipients = toggleNotificationRecipientResolver == null ? Set.of() : toggleNotificationRecipientResolver.byPermission(permissionCode);
        systemPermissionRepository.toggleStatus(permissionCode, actor);
        PermissionResponse response = findById(permissionCode, authentication);
        notifyUsers(recipients, "PERMISSION_TOGGLED");
        if (toggleNotificationDispatcher != null) {
            toggleNotificationDispatcher.dispatch(recipients, "PERMISSION_TOGGLED", permissionLabel(response), Boolean.TRUE.equals(response.getStatus()), actor);
        }
        return response;
    }

    private void notifyPermissionChanged(String permissionCode, String reason) {
        if (authRefreshNotifier != null) {
            authRefreshNotifier.notifyPermissionChanged(permissionCode, reason);
        }
    }

    private void notifyUsers(Set<String> recipients, String reason) {
        if (authRefreshNotifier != null) {
            authRefreshNotifier.notifyUsers(recipients, reason);
        }
    }

    private String permissionLabel(PermissionResponse permission) {
        if (permission == null) {
            return "quyền chưa xác định";
        }
        String name = commonValidationHelper.normalizeOptionalText(permission.getPermissionName());
        String code = commonValidationHelper.normalizeOptionalText(permission.getPermissionCode());
        if (name != null) {
            return name;
        }
        return name != null ? name : code != null ? code : "quyền chưa xác định";
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
                .createdBy(permission.getCreatedBy())
                .createdByName(auditActorNameResolver.resolve(permission.getCreatedBy()))
                .updatedAt(permission.getUpdatedAt())
                .updatedBy(permission.getUpdatedBy())
                .updatedByName(auditActorNameResolver.resolve(permission.getUpdatedBy()))
                .build();
    }

}
