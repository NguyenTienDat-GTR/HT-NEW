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
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.request.RoleCreateRequest;
import phucuong.ht.modules.system.model.request.RoleUpdateRequest;
import phucuong.ht.modules.system.model.response.RoleResponse;
import phucuong.ht.modules.system.repository.SystemRoleRepository;

import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleManagementService {

    private final SystemRoleRepository systemRoleRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final SystemAdminAuthorizationHelper authorizationHelper;

    public RoleResponse create(RoleCreateRequest request, Authentication authentication) {
        if (systemRoleRepository.findById(request.getRoleCode()).isPresent()) {
            throw commonValidationHelper.conflict("Vai trò đã tồn tại: " + request.getRoleCode());
        }
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo vai trò");
        request.setRoleCode(commonValidationHelper.normalizeRequiredText(request.getRoleCode()));
        request.setRoleName(commonValidationHelper.normalizeRequiredText(request.getRoleName()));
        request.setDescription(commonValidationHelper.normalizeOptionalText(request.getDescription()));
        Role role = systemRoleRepository.createRole(request, actor);
        return toResponse(role);
    }

    public Page<RoleResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        List<Role> manageable = loadAllMatching(request).stream()
                .filter(role -> authorizationHelper.canReadRole(authentication, role))
                .toList();
        return page(request, manageable);
    }

    public RoleResponse findById(String roleCode, Authentication authentication) {
        Role role = systemRoleRepository.findById(roleCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò: " + roleCode));
        authorizationHelper.requireRoleReadAccess(authentication, role);
        return toResponse(role);
    }

    public RoleResponse update(String roleCode, RoleUpdateRequest request, Authentication authentication) {
        Role role = systemRoleRepository.findById(roleCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò: " + roleCode));
        if (request.getRoleName() != null) {
            role.setRoleName(commonValidationHelper.normalizeRequiredText(request.getRoleName()));
        }
        if (request.getDescription() != null) {
            role.setDescription(commonValidationHelper.normalizeOptionalText(request.getDescription()));
        }
        if (request.getDisplayOrder() != null) {
            role.setDisplayOrder(request.getDisplayOrder());
        }
        role.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật vai trò"));
        return toResponse(role);
    }

    public void delete(String roleCode) {
        systemRoleRepository.delete(roleCode);
    }

    public RoleResponse toggleStatus(String roleCode, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái vai trò");
        systemRoleRepository.toggleStatus(roleCode, actor);
        return findById(roleCode, authentication);
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
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}
