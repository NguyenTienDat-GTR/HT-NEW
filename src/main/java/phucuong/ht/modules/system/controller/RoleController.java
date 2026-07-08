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
import phucuong.ht.modules.system.model.request.RoleCreateRequest;
import phucuong.ht.modules.system.model.request.RoleUpdateRequest;
import phucuong.ht.modules.system.model.response.RoleResponse;
import phucuong.ht.modules.system.service.RoleManagementService;

@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleManagementService roleManagementService;

    @PostMapping
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.role.create.all')")
    public apiResponse<RoleResponse> create(@Valid @RequestBody RoleCreateRequest request, Authentication authentication) {
        return apiResponse.success("Create role successfully", roleManagementService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.role.read.all')")
    public apiResponse<Page<RoleResponse>> findAll(@ModelAttribute BaseSearchRequest request) {
        return apiResponse.success("Get role list successfully", roleManagementService.findAll(request));
    }

    @GetMapping("/{roleCode}")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.role.read.all')")
    public apiResponse<RoleResponse> findById(@PathVariable String roleCode) {
        return apiResponse.success("Get role successfully", roleManagementService.findById(roleCode));
    }

    @PutMapping("/{roleCode}")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.role.update.all')")
    public apiResponse<RoleResponse> update(
            @PathVariable String roleCode,
            @Valid @RequestBody RoleUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Update role successfully", roleManagementService.update(roleCode, request, authentication));
    }

    @DeleteMapping("/{roleCode}")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.role.delete.all')")
    public apiResponse<Void> delete(@PathVariable String roleCode) {
        roleManagementService.delete(roleCode);
        return apiResponse.success("Delete role successfully", null);
    }

    @PatchMapping("/{roleCode}/toggle-status")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.role.update.all')")
    public apiResponse<RoleResponse> toggleStatus(
            @PathVariable String roleCode,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Toggle role status successfully", roleManagementService.toggleStatus(roleCode, request, authentication));
    }
}
