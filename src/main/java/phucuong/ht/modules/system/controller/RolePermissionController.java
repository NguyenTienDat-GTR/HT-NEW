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
import phucuong.ht.modules.system.model.request.RolePermissionCreateRequest;
import phucuong.ht.modules.system.model.request.RolePermissionUpdateRequest;
import phucuong.ht.modules.system.model.response.RolePermissionResponse;
import phucuong.ht.modules.system.service.RolePermissionManagementService;

@RestController
@RequestMapping("/api/system/role-permissions")
@RequiredArgsConstructor
public class RolePermissionController {

    private final RolePermissionManagementService rolePermissionManagementService;

    @PostMapping
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.role_permission.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.diocese')"
    )
    public apiResponse<RolePermissionResponse> create(@Valid @RequestBody RolePermissionCreateRequest request, Authentication authentication) {
        return apiResponse.success("Gán quyền cho vai trò thành công", rolePermissionManagementService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.role_permission.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.diocese')"
    )
    public apiResponse<Page<RolePermissionResponse>> findAll(@ModelAttribute BaseSearchRequest request) {
        return apiResponse.success("Lấy danh sách quyền của vai trò thành công", rolePermissionManagementService.findAll(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.role_permission.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.diocese')"
    )
    public apiResponse<RolePermissionResponse> findById(@PathVariable String id) {
        return apiResponse.success("Lấy thông tin quyền của vai trò thành công", rolePermissionManagementService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.role_permission.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.diocese')"
    )
    public apiResponse<RolePermissionResponse> update(
            @PathVariable String id,
            @Valid @RequestBody RolePermissionUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật quyền của vai trò thành công", rolePermissionManagementService.update(id, request, authentication));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.diocese')"
    )
    public apiResponse<Void> delete(@PathVariable String id, Authentication authentication) {
        rolePermissionManagementService.delete(id, authentication);
        return apiResponse.success("Xóa quyền của vai trò thành công", null);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.role_permission.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.diocese')"
    )
    public apiResponse<RolePermissionResponse> toggleStatus(
            @PathVariable String id,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái quyền của vai trò thành công", rolePermissionManagementService.toggleStatus(id, request, authentication));
    }
}
