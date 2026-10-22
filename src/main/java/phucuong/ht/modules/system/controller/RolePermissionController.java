package phucuong.ht.modules.system.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.system.model.request.RolePermissionBulkRequest;
import phucuong.ht.modules.system.model.request.RolePermissionRevokeRequest;
import phucuong.ht.modules.system.model.request.RolePermissionCreateRequest;
import phucuong.ht.modules.system.model.request.RolePermissionUpdateRequest;
import phucuong.ht.modules.system.model.response.PermissionResponse;
import phucuong.ht.modules.system.model.response.RoleResponse;
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
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.parish')"
    )
    public apiResponse<RolePermissionResponse> create(@Valid @RequestBody RolePermissionCreateRequest request, Authentication authentication) {
        return apiResponse.success("Gán quyền cho vai trò thành công", rolePermissionManagementService.create(request, authentication));
    }

    @PostMapping("/bulk")
    @PreAuthorize(
            "@systemAdminAuthorizationHelper.isSuperAdmin(authentication)"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.parish')"
    )
    public apiResponse<List<RolePermissionResponse>> bulkAssign(@Valid @RequestBody RolePermissionBulkRequest request, Authentication authentication) {
        return apiResponse.success("Gán nhiều quyền cho vai trò thành công", rolePermissionManagementService.bulkAssign(request, authentication));
    }

    @GetMapping
    @PreAuthorize(
            "@systemAdminAuthorizationHelper.isSuperAdmin(authentication)"
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
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.parish')"
    )
    public apiResponse<Page<RolePermissionResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách quyền của vai trò thành công", rolePermissionManagementService.findAll(request, authentication));
    }

    @GetMapping("/manageable-roles")
    @PreAuthorize(
            "@systemAdminAuthorizationHelper.isSuperAdmin(authentication)"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.parish')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.parish')"
    )
    public apiResponse<List<RoleResponse>> findManageableRoles(
            @RequestParam(defaultValue = "assign") String action,
            Authentication authentication
    ) {
        return apiResponse.success("Lấy danh sách vai trò được phân quyền thành công", rolePermissionManagementService.findManageableRoles(action, authentication));
    }

    @GetMapping("/assignable-permissions")
    @PreAuthorize(
            "@systemAdminAuthorizationHelper.isSuperAdmin(authentication)"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.parish')"
    )
    public apiResponse<List<PermissionResponse>> findAssignablePermissions(
            @RequestParam String roleCode,
            Authentication authentication
    ) {
        return apiResponse.success("Lấy danh sách quyền chưa gán cho vai trò thành công", rolePermissionManagementService.findAssignablePermissions(roleCode, authentication));
    }

    @PostMapping("/revoke")
    @PreAuthorize(
            "@systemAdminAuthorizationHelper.isSuperAdmin(authentication)"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.parish')"
    )
    public apiResponse<Integer> bulkRevoke(@Valid @RequestBody RolePermissionRevokeRequest request, Authentication authentication) {
        return apiResponse.success("Thu hồi quyền của vai trò thành công", rolePermissionManagementService.bulkRevoke(request, authentication));
    }

    @GetMapping("/revocable-permissions")
    @PreAuthorize(
            "@systemAdminAuthorizationHelper.isSuperAdmin(authentication)"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.parish')"
    )
    public apiResponse<List<PermissionResponse>> findRevocablePermissions(@RequestParam String roleCode, Authentication authentication) {
        return apiResponse.success("Lấy danh sách quyền có thể thu hồi của vai trò thành công", rolePermissionManagementService.findRevocablePermissions(roleCode, authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize(
            "@systemAdminAuthorizationHelper.isSuperAdmin(authentication)"
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
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.revoke.parish')"
    )
    public apiResponse<RolePermissionResponse> findById(@PathVariable String id, Authentication authentication) {
        return apiResponse.success("Lấy thông tin quyền của vai trò thành công", rolePermissionManagementService.findById(id, authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize(
            "@systemAdminAuthorizationHelper.isSuperAdmin(authentication)"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role_permission.assign.parish')"
    )
    public apiResponse<RolePermissionResponse> update(
            @PathVariable String id,
            @Valid @RequestBody RolePermissionUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật quyền của vai trò thành công", rolePermissionManagementService.update(id, request, authentication));
    }
}
