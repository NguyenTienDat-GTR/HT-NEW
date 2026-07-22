package phucuong.ht.modules.system.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.system.model.request.PermissionUpdateRequest;
import phucuong.ht.modules.system.model.response.PermissionResponse;
import phucuong.ht.modules.system.model.response.PermissionTaxonomyResponse;
import phucuong.ht.modules.system.service.PermissionManagementService;

@RestController
@RequestMapping("/api/system/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionManagementService permissionManagementService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.read.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.read.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.read.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.read.parish')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.parish')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.parish')")
    public apiResponse<Page<PermissionResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách quyền thành công", permissionManagementService.findAll(request, authentication));
    }

    @GetMapping("/taxonomy")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public apiResponse<PermissionTaxonomyResponse> taxonomy() {
        return apiResponse.success("Lấy taxonomy quyền thành công", permissionManagementService.taxonomy());
    }

    @GetMapping("/{permissionCode}")
    @PreAuthorize("hasRole('SUPER_ADMIN')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.read.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.read.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.read.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.read.parish')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.parish')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.parish')")
    public apiResponse<PermissionResponse> findById(@PathVariable String permissionCode, Authentication authentication) {
        return apiResponse.success("Lấy thông tin quyền thành công", permissionManagementService.findById(permissionCode, authentication));
    }

    @PutMapping("/{permissionCode}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public apiResponse<PermissionResponse> update(
            @PathVariable String permissionCode,
            @Valid @RequestBody PermissionUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật quyền thành công", permissionManagementService.update(permissionCode, request, authentication));
    }
    @PatchMapping("/{permissionCode}/toggle-status")
    @PreAuthorize("hasRole('SUPER_ADMIN') && @securityUtils.hasAuthority(authentication, 'system.permission.toggle.all')")
    public apiResponse<PermissionResponse> toggleStatus(
            @PathVariable String permissionCode,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái quyền thành công", permissionManagementService.toggleStatus(permissionCode, request, authentication));
    }
}
