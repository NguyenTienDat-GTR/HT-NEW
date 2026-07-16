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

    private static final String CAN_CREATE_ROLE =
            "@securityUtils.hasAuthority(authentication, 'system.role.create.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role.create.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role.create.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role.create.parish')";
    private static final String CAN_READ_ROLE =
            "@securityUtils.hasAuthority(authentication, 'system.role.read.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role.read.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role.read.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role.read.parish')";
    private static final String CAN_UPDATE_ROLE =
            "@securityUtils.hasAuthority(authentication, 'system.role.update.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role.update.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role.update.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role.update.parish')";
    private static final String CAN_TOGGLE_ROLE =
            "@securityUtils.hasAuthority(authentication, 'system.role.toggle.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role.toggle.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role.toggle.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.role.toggle.parish')";

    private final RoleManagementService roleManagementService;

    @PostMapping
    @PreAuthorize(CAN_CREATE_ROLE)
    public apiResponse<RoleResponse> create(@Valid @RequestBody RoleCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tạo vai trò thành công", roleManagementService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize(CAN_READ_ROLE)
    public apiResponse<Page<RoleResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách vai trò thành công", roleManagementService.findAll(request, authentication));
    }

    @GetMapping("/{roleCode}")
    @PreAuthorize(CAN_READ_ROLE)
    public apiResponse<RoleResponse> findById(@PathVariable String roleCode, Authentication authentication) {
        return apiResponse.success("Lấy thông tin vai trò thành công", roleManagementService.findById(roleCode, authentication));
    }

    @PutMapping("/{roleCode}")
    @PreAuthorize(CAN_UPDATE_ROLE)
    public apiResponse<RoleResponse> update(
            @PathVariable String roleCode,
            @Valid @RequestBody RoleUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật vai trò thành công", roleManagementService.update(roleCode, request, authentication));
    }

    @PatchMapping("/{roleCode}/toggle-status")
    @PreAuthorize(CAN_TOGGLE_ROLE)
    public apiResponse<RoleResponse> toggleStatus(
            @PathVariable String roleCode,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái vai trò thành công", roleManagementService.toggleStatus(roleCode, request, authentication));
    }
}
