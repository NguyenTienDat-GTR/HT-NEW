package phucuong.ht.modules.system.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.system.model.request.RolePermissionBulkRequest;
import phucuong.ht.modules.system.model.request.RolePermissionCreateRequest;
import phucuong.ht.modules.system.model.request.RolePermissionUpdateRequest;
import phucuong.ht.modules.system.model.response.RolePermissionResponse;
import phucuong.ht.modules.system.service.RolePermissionManagementService;

import java.util.List;

@RestController
@RequestMapping("/api/system/role-permissions")
@RequiredArgsConstructor
public class RolePermissionController {

    private final RolePermissionManagementService rolePermissionManagementService;

    @PostMapping
    @PreAuthorize(
            "@systemAdminAuthorizationHelper.isSuperAdmin(authentication)"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.diocese')"
    )
    public apiResponse<RolePermissionResponse> create(@Valid @RequestBody RolePermissionCreateRequest request, Authentication authentication) {
        return apiResponse.success("Gan quyen cho vai tro thanh cong", rolePermissionManagementService.create(request, authentication));
    }

    @PostMapping("/bulk")
    @PreAuthorize(
            "@systemAdminAuthorizationHelper.isSuperAdmin(authentication)"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.diocese')"
    )
    public apiResponse<List<RolePermissionResponse>> bulkAssign(@Valid @RequestBody RolePermissionBulkRequest request, Authentication authentication) {
        return apiResponse.success("Gan nhieu quyen cho vai tro thanh cong", rolePermissionManagementService.bulkAssign(request, authentication));
    }

    @GetMapping
    @PreAuthorize(
            "@systemAdminAuthorizationHelper.isSuperAdmin(authentication)"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.diocese')"
    )
    public apiResponse<Page<RolePermissionResponse>> findAll(@ModelAttribute BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lay danh sach quyen cua vai tro thanh cong", rolePermissionManagementService.findAll(request, authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize(
            "@systemAdminAuthorizationHelper.isSuperAdmin(authentication)"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.diocese')"
    )
    public apiResponse<RolePermissionResponse> findById(@PathVariable String id, Authentication authentication) {
        return apiResponse.success("Lay thong tin quyen cua vai tro thanh cong", rolePermissionManagementService.findById(id, authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize(
            "@systemAdminAuthorizationHelper.isSuperAdmin(authentication)"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.diocese')"
    )
    public apiResponse<RolePermissionResponse> update(
            @PathVariable String id,
            @Valid @RequestBody RolePermissionUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cap nhat quyen cua vai tro thanh cong", rolePermissionManagementService.update(id, request, authentication));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(
            "@systemAdminAuthorizationHelper.isSuperAdmin(authentication)"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.diocese')"
    )
    public apiResponse<Void> delete(@PathVariable String id, Authentication authentication) {
        rolePermissionManagementService.delete(id, authentication);
        return apiResponse.success("Xoa quyen cua vai tro thanh cong", null);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize(
            "@systemAdminAuthorizationHelper.isSuperAdmin(authentication)"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.diocese')"
    )
    public apiResponse<RolePermissionResponse> toggleStatus(
            @PathVariable String id,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Doi trang thai quyen cua vai tro thanh cong", rolePermissionManagementService.toggleStatus(id, request, authentication));
    }
}
