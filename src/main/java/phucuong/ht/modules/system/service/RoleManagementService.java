package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

    public RoleResponse create(RoleCreateRequest request, Authentication authentication) {
        if (systemRoleRepository.findById(request.getRoleCode()).isPresent()) {
            throw commonValidationHelper.conflict("Vai trò đã tồn tại: " + request.getRoleCode());
        }
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo vai trò");
        request.setRoleCode(commonValidationHelper.normalizeRequiredText(request.getRoleCode()));
        request.setRoleName(commonValidationHelper.normalizeRequiredText(request.getRoleName()));
        request.setDescription(commonValidationHelper.normalizeOptionalText(request.getDescription()));
        request.setIsSystem(Boolean.FALSE);
        Role role = systemRoleRepository.createRole(request, actor);
        return toResponse(role);
    }

    public Page<RoleResponse> findAll(BaseSearchRequest request) {
        Page<Role> page = systemRoleRepository.findAll(copyRequest(request));
        return new PageImpl<>(page.getContent().stream().map(this::toResponse).toList(), page.getPageable(), page.getTotalElements());
    }

    public RoleResponse findById(String roleCode) {
        return toResponse(systemRoleRepository.findById(roleCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy vai trò: " + roleCode)));
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
        return findById(roleCode);
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
