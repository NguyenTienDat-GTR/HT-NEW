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
import phucuong.ht.modules.system.model.request.AccountPermissionOverrideCreateRequest;
import phucuong.ht.modules.system.model.request.AccountPermissionOverrideUpdateRequest;
import phucuong.ht.modules.system.model.response.AccountPermissionOverrideResponse;
import phucuong.ht.modules.system.service.AccountPermissionOverrideManagementService;

@RestController
@RequestMapping("/api/system/account-permissions")
@RequiredArgsConstructor
public class AccountPermissionOverrideController {

    private final AccountPermissionOverrideManagementService accountPermissionOverrideManagementService;

    @PostMapping
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.account_permission.manage.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_permission.manage.diocese')"
    )
    public apiResponse<AccountPermissionOverrideResponse> create(
            @Valid @RequestBody AccountPermissionOverrideCreateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Gán quyền cho tài khoản thành công", accountPermissionOverrideManagementService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.account_permission.manage.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_permission.manage.diocese')"
    )
    public apiResponse<Page<AccountPermissionOverrideResponse>> findAll(@ModelAttribute BaseSearchRequest request) {
        return apiResponse.success("Lấy danh sách quyền của tài khoản thành công", accountPermissionOverrideManagementService.findAll(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.account_permission.manage.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_permission.manage.diocese')"
    )
    public apiResponse<AccountPermissionOverrideResponse> findById(@PathVariable String id) {
        return apiResponse.success("Lấy thông tin quyền của tài khoản thành công", accountPermissionOverrideManagementService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.account_permission.manage.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_permission.manage.diocese')"
    )
    public apiResponse<AccountPermissionOverrideResponse> update(
            @PathVariable String id,
            @Valid @RequestBody AccountPermissionOverrideUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật quyền của tài khoản thành công", accountPermissionOverrideManagementService.update(id, request, authentication));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.account_permission.manage.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_permission.manage.diocese')"
    )
    public apiResponse<Void> delete(@PathVariable String id, Authentication authentication) {
        accountPermissionOverrideManagementService.delete(id, authentication);
        return apiResponse.success("Xóa quyền của tài khoản thành công", null);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize(
            "@securityUtils.hasAuthority(authentication, 'system.account_permission.manage.all')"
                    + " || @securityUtils.hasAuthority(authentication, 'system.account_permission.manage.diocese')"
    )
    public apiResponse<AccountPermissionOverrideResponse> toggleStatus(
            @PathVariable String id,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái quyền của tài khoản thành công", accountPermissionOverrideManagementService.toggleStatus(id, request, authentication));
    }
}
