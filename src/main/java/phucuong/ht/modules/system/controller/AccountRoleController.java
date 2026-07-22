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
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.system.model.request.AccountRoleBulkRequest;
import phucuong.ht.modules.system.model.request.AccountRoleCreateRequest;
import phucuong.ht.modules.system.model.request.AccountRoleUpdateRequest;
import phucuong.ht.modules.system.model.response.AccountRoleResponse;
import phucuong.ht.modules.system.model.response.RoleResponse;
import phucuong.ht.modules.system.service.AccountRoleService;

import java.util.List;

@RestController
@RequestMapping("/api/system/account-roles")
@RequiredArgsConstructor
public class AccountRoleController {

    private final AccountRoleService accountRoleService;

    @PostMapping
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.account_role.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.parish')"
    )
    public apiResponse<AccountRoleResponse> assignRole(
            @Valid @RequestBody AccountRoleCreateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Gán vai trò thành công", accountRoleService.assignRole(request, authentication));
    }

    @PostMapping("/bulk")
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.account_role.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.parish')"
    )
    public apiResponse<List<AccountRoleResponse>> bulkAssign(
            @Valid @RequestBody AccountRoleBulkRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Gán nhiều vai trò thành công", accountRoleService.bulkAssign(request, authentication));
    }

    @GetMapping
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.account_role.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.parish')"
    )
    public apiResponse<Page<AccountRoleResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách vai trò tài khoản thành công", accountRoleService.findAll(request, authentication));
    }

    @GetMapping("/assignable-roles")
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.account_role.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.parish')"
    )
    public apiResponse<List<RoleResponse>> findAssignableRoles(
            @RequestParam(required = false) String username,
            Authentication authentication
    ) {
        return apiResponse.success("Lấy danh sách vai trò có thể gán thành công", accountRoleService.findAssignableRoles(username, authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.account_role.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.parish')"
    )
    public apiResponse<AccountRoleResponse> findById(@PathVariable String id, Authentication authentication) {
        return apiResponse.success("Lấy chi tiết vai trò tài khoản thành công", accountRoleService.findById(id, authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.account_role.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.parish')"
    )
    public apiResponse<AccountRoleResponse> update(
            @PathVariable String id,
            @RequestBody AccountRoleUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật vai trò tài khoản thành công", accountRoleService.update(id, request, authentication));
    }
    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.account_role.toggle.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.diocese')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.deanery')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.parish')"
    )
    public apiResponse<AccountRoleResponse> toggle(@PathVariable String id, @Valid @RequestBody StatusToggleRequest request, Authentication authentication) {
        return apiResponse.success("Đổi trạng thái vai trò tài khoản thành công", accountRoleService.toggleStatus(id, request, authentication));
    }
}
