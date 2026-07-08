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
import phucuong.ht.modules.system.model.request.PermissionCreateRequest;
import phucuong.ht.modules.system.model.request.PermissionUpdateRequest;
import phucuong.ht.modules.system.model.response.PermissionResponse;
import phucuong.ht.modules.system.service.PermissionManagementService;

@RestController
@RequestMapping("/api/system/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionManagementService permissionManagementService;

    @PostMapping
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.permission.create.all')")
    public apiResponse<PermissionResponse> create(@Valid @RequestBody PermissionCreateRequest request, Authentication authentication) {
        return apiResponse.success("Create permission successfully", permissionManagementService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.permission.read.all')")
    public apiResponse<Page<PermissionResponse>> findAll(@ModelAttribute BaseSearchRequest request) {
        return apiResponse.success("Get permission list successfully", permissionManagementService.findAll(request));
    }

    @GetMapping("/{permissionCode}")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.permission.read.all')")
    public apiResponse<PermissionResponse> findById(@PathVariable String permissionCode) {
        return apiResponse.success("Get permission successfully", permissionManagementService.findById(permissionCode));
    }

    @PutMapping("/{permissionCode}")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.permission.update.all')")
    public apiResponse<PermissionResponse> update(
            @PathVariable String permissionCode,
            @Valid @RequestBody PermissionUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Update permission successfully", permissionManagementService.update(permissionCode, request, authentication));
    }

    @DeleteMapping("/{permissionCode}")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.permission.delete.all')")
    public apiResponse<Void> delete(@PathVariable String permissionCode) {
        permissionManagementService.delete(permissionCode);
        return apiResponse.success("Delete permission successfully", null);
    }

    @PatchMapping("/{permissionCode}/toggle-status")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.permission.update.all')")
    public apiResponse<PermissionResponse> toggleStatus(
            @PathVariable String permissionCode,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Toggle permission status successfully", permissionManagementService.toggleStatus(permissionCode, request, authentication));
    }
}
