package phucuong.ht.modules.system.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import phucuong.ht.modules.system.model.request.AccountPermissionOverrideBulkRequest;
import phucuong.ht.modules.system.model.request.AccountPermissionOverrideCreateRequest;
import phucuong.ht.modules.system.model.request.AccountPermissionOverrideUpdateRequest;
import phucuong.ht.modules.system.model.response.AccountPermissionOverrideResponse;
import phucuong.ht.modules.system.model.response.PermissionResponse;
import phucuong.ht.modules.system.service.AccountPermissionOverrideManagementService;

import java.util.List;

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
        return apiResponse.success("Gan quyen cho tai khoan thanh cong", service.create(request, authentication));
    }

    @PostMapping("/bulk")
    public apiResponse<List<AccountPermissionOverrideResponse>> bulkAssign(@Valid @RequestBody AccountPermissionOverrideBulkRequest request, Authentication authentication) {
        return apiResponse.success("Gan nhieu quyen cho tai khoan thanh cong", service.bulkAssign(request, authentication));
    }

    @GetMapping
    public apiResponse<Page<AccountPermissionOverrideResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lay danh sach quyen cua tai khoan thanh cong", service.findAll(request, authentication));
    }

    @GetMapping("/assignable-permissions")
    public apiResponse<List<PermissionResponse>> findAssignablePermissions(
            @RequestParam String username,
            Authentication authentication
    ) {
        return apiResponse.success("Lấy danh sách quyền chưa gán cho tài khoản thành công", service.findAssignablePermissions(username, authentication));
    }

    @GetMapping("/{id}")
    public apiResponse<AccountPermissionOverrideResponse> findById(@PathVariable String id, Authentication authentication) {
        return apiResponse.success("Lay thong tin quyen cua tai khoan thanh cong", service.findById(id, authentication));
    }

    @PutMapping("/{id}")
    public apiResponse<AccountPermissionOverrideResponse> update(@PathVariable String id, @Valid @RequestBody AccountPermissionOverrideUpdateRequest request, Authentication authentication) {
        return apiResponse.success("Cap nhat quyen cua tai khoan thanh cong", service.update(id, request, authentication));
    }

    @DeleteMapping("/{id}")
    public apiResponse<Void> delete(@PathVariable String id, Authentication authentication) {
        service.delete(id, authentication);
        return apiResponse.success("Xoa quyen cua tai khoan thanh cong", null);
    }

    @PatchMapping("/{id}/toggle-status")
    public apiResponse<AccountPermissionOverrideResponse> toggleStatus(@PathVariable String id, @Valid @RequestBody StatusToggleRequest request, Authentication authentication) {
        return apiResponse.success("Doi trang thai quyen cua tai khoan thanh cong", service.toggleStatus(id, request, authentication));
    }
}
