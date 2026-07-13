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
@PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account_permission.manage.all')"
        + " || @securityUtils.hasAuthority(authentication, 'system.account_permission.manage.diocese')"
        + " || @securityUtils.hasAuthority(authentication, 'system.account_permission.manage.deanery')"
        + " || @securityUtils.hasAuthority(authentication, 'system.account_permission.manage.parish')")
public class AccountPermissionOverrideController {
    private final AccountPermissionOverrideManagementService service;

    @PostMapping
    public apiResponse<AccountPermissionOverrideResponse> create(@Valid @RequestBody AccountPermissionOverrideCreateRequest request, Authentication authentication) {
        return apiResponse.success("Gán quyền cho tài khoản thành công", service.create(request, authentication));
    }

    @GetMapping
    public apiResponse<Page<AccountPermissionOverrideResponse>> findAll(@ModelAttribute BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách quyền của tài khoản thành công", service.findAll(request, authentication));
    }

    @GetMapping("/{id}")
    public apiResponse<AccountPermissionOverrideResponse> findById(@PathVariable String id, Authentication authentication) {
        return apiResponse.success("Lấy thông tin quyền của tài khoản thành công", service.findById(id, authentication));
    }

    @PutMapping("/{id}")
    public apiResponse<AccountPermissionOverrideResponse> update(@PathVariable String id, @Valid @RequestBody AccountPermissionOverrideUpdateRequest request, Authentication authentication) {
        return apiResponse.success("Cập nhật quyền của tài khoản thành công", service.update(id, request, authentication));
    }

    @DeleteMapping("/{id}")
    public apiResponse<Void> delete(@PathVariable String id, Authentication authentication) {
        service.delete(id, authentication);
        return apiResponse.success("Xóa quyền của tài khoản thành công", null);
    }

    @PatchMapping("/{id}/toggle-status")
    public apiResponse<AccountPermissionOverrideResponse> toggleStatus(@PathVariable String id, @Valid @RequestBody StatusToggleRequest request, Authentication authentication) {
        return apiResponse.success("Đổi trạng thái quyền của tài khoản thành công", service.toggleStatus(id, request, authentication));
    }
}
