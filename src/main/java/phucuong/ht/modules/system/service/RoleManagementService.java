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
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.request.RoleCreateRequest;
import phucuong.ht.modules.system.model.request.RoleUpdateRequest;
import phucuong.ht.modules.system.model.response.RoleResponse;
import phucuong.ht.modules.system.repository.SystemRoleRepository;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleManagementService {

    private final SystemRoleRepository systemRoleRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final SystemAdminAuthorizationHelper authorizationHelper;
    @Autowired(required = false)
    private ToggleNotificationRecipientResolver toggleNotificationRecipientResolver;
    @Autowired(required = false)
    private ToggleNotificationDispatcher toggleNotificationDispatcher;
    @Autowired(required = false)
    private RbacAuthRefreshNotifier authRefreshNotifier;

    @Transactional
    public RoleResponse create(RoleCreateRequest request, Authentication authentication) {
        authorizationHelper.requireRoleCreateAccess(authentication);
        authorizationHelper.requireRoleDisplayOrderManageable(authentication, request.getDisplayOrder());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo vai trò");
        request.setRoleName(commonValidationHelper.normalizeRequiredText(request.getRoleName()));
        request.setRoleCode(nextAvailableRoleCode(request.getRoleName()));
        request.setDescription(commonValidationHelper.normalizeOptionalText(request.getDescription()));
        Role role = systemRoleRepository.createRole(request, actor);
        return toResponse(role);
    }

    public Page<RoleResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        List<Role> manageable = loadAllMatching(request).stream()
                .filter(role -> !authorizationHelper.isSuperAdmin(authentication) || isVisibleRole(role))
                .filter(role -> authorizationHelper.canReadRole(authentication, role))
                .toList();
        return page(request, manageable);
    }

    public RoleResponse findById(String roleCode, Authentication authentication) {
        Role role = systemRoleRepository.findById(roleCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò: " + roleCode));
        if (authorizationHelper.isSuperAdmin(authentication) && !isVisibleRole(role)) {
            throw commonValidationHelper.forbidden("Vai trò không thuộc phạm vi hiển thị của SUPER_ADMIN");
        }
        authorizationHelper.requireRoleReadAccess(authentication, role);
        return toResponse(role);
    }

    @Transactional
    public RoleResponse update(String roleCode, RoleUpdateRequest request, Authentication authentication) {
        Role role = systemRoleRepository.findById(roleCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò: " + roleCode));
        authorizationHelper.requireRoleWriteAccess(authentication, role, "update");
        authorizationHelper.requireRoleDisplayOrderManageable(authentication, request.getDisplayOrder());
        if (request.getRoleName() != null) {
            role.setRoleName(commonValidationHelper.normalizeRequiredText(request.getRoleName()));
        }
        if (request.getDescription() != null) {
            role.setDescription(commonValidationHelper.normalizeOptionalText(request.getDescription()));
        }
        if (request.getDisplayOrder() != null) {
            role.setDisplayOrder(request.getDisplayOrder());
        }
        role.setUpdatedAt(OffsetDateTime.now());
        role.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật vai trò"));
        Role saved = systemRoleRepository.save(role);
        notifyRoleChanged(roleCode, "ROLE_UPDATED");
        return toResponse(saved);
    }

    @Transactional
    public RoleResponse toggleStatus(String roleCode, StatusToggleRequest request, Authentication authentication) {
        Role role = systemRoleRepository.findById(roleCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò: " + roleCode));
        authorizationHelper.requireRoleWriteAccess(authentication, role, "toggle");
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw commonValidationHelper.forbidden("Không được đổi trạng thái vai trò hệ thống");
        }
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái vai trò");
        Set<String> recipients = toggleNotificationRecipientResolver == null ? Set.of() : toggleNotificationRecipientResolver.byRole(roleCode);
        Role updatedRole = systemRoleRepository.toggleStatus(roleCode, actor);
        notifyUsers(recipients, "ROLE_TOGGLED");
        if (toggleNotificationDispatcher != null) {
            toggleNotificationDispatcher.dispatch(recipients, "ROLE_TOGGLED", roleLabel(updatedRole), Boolean.TRUE.equals(updatedRole.getStatus()), actor);
        }
        return toResponse(updatedRole);
    }

    private void notifyRoleChanged(String roleCode, String reason) {
        if (authRefreshNotifier != null) {
            authRefreshNotifier.notifyRoleChanged(roleCode, reason);
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
        if (name != null && code != null) {
            return name + " (" + code + ")";
        }
        return name != null ? name : code != null ? code : "vai trò chưa xác định";
    }

    private String nextAvailableRoleCode(String roleName) {
        String base = normalizeRoleCodeBase(roleName);
        if (base.isBlank()) {
            throw commonValidationHelper.validationError("Không thể sinh mã vai trò từ tên vai trò");
        }
        if (!systemRoleRepository.existsByRoleCode(base)) return base;
        int suffix = 2;
        while (systemRoleRepository.existsByRoleCode(base + suffix)) suffix++;
        return base + suffix;
    }

    private String normalizeRoleCodeBase(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replace("đ", "d")
                .replace("Đ", "D");
        return normalized
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toUpperCase(Locale.ROOT);
    }

    private boolean isVisibleRole(Role role) {
        return Boolean.TRUE.equals(role.getIsSystem()) || "super-admin".equalsIgnoreCase(role.getCreatedBy());
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

    private List<Role> loadAllMatching(BaseSearchRequest request) {
        List<Role> matches = new java.util.ArrayList<>();
        int page = 0;
        while (true) {
            BaseSearchRequest batch = copyRequest(request);
            batch.setPage(page);
            batch.setSize(BaseSearchRequest.MAX_PAGE_SIZE);
            Page<Role> result = systemRoleRepository.findAll(batch);
            matches.addAll(result.getContent());
            if (!result.hasNext()) {
                return matches;
            }
            page++;
        }
    }

    private Page<RoleResponse> page(BaseSearchRequest request, List<Role> roles) {
        int page = request == null ? 0 : request.getPage();
        int size = request == null ? 10 : request.getSize();
        int start = Math.min(page * size, roles.size());
        int end = Math.min(start + size, roles.size());
        return new PageImpl<>(roles.subList(start, end).stream().map(this::toResponse).toList(), PageRequest.of(page, size), roles.size());
    }

    private RoleResponse toResponse(Role role) {
        return RoleResponse.builder()
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .isSystem(role.getIsSystem())
                .displayOrder(role.getDisplayOrder())
                .status(role.getStatus())
                .createdAt(role.getCreatedAt())
                .createdBy(role.getCreatedBy())
                .updatedAt(role.getUpdatedAt())
                .updatedBy(role.getUpdatedBy())
                .build();
    }
}
