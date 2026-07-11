package phucuong.ht.modules.system.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.modules.system.model.request.AccountCreateRequest;
import phucuong.ht.modules.system.model.request.AccountUpdateRequest;
import phucuong.ht.modules.system.model.response.AccountResponse;
import phucuong.ht.modules.system.service.AccountManagementService;

@RestController
@RequestMapping("/api/system/accounts")
@RequiredArgsConstructor
@PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account.manage.all')"
        + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.diocese')"
        + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.deanery')"
        + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.parish')")
public class AccountController {
    private final AccountManagementService service;

    @PostMapping
    public apiResponse<AccountResponse> create(@Valid @RequestBody AccountCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tạo tài khoản thành công", service.create(request, authentication));
    }

    @GetMapping
    public apiResponse<Page<AccountResponse>> findAll(Pageable pageable, Authentication authentication) {
        return apiResponse.success("Lấy danh sách tài khoản thành công", service.findAll(pageable, authentication));
    }

    @GetMapping("/{username}")
    public apiResponse<AccountResponse> findById(@PathVariable String username, Authentication authentication) {
        return apiResponse.success("Lấy thông tin tài khoản thành công", service.findById(username, authentication));
    }

    @PutMapping("/{username}")
    public apiResponse<AccountResponse> update(@PathVariable String username, @RequestBody AccountUpdateRequest request, Authentication authentication) {
        return apiResponse.success("Cập nhật tài khoản thành công", service.update(username, request, authentication));
    }

    @DeleteMapping("/{username}")
    public apiResponse<Void> delete(@PathVariable String username, Authentication authentication) {
        service.delete(username, authentication);
        return apiResponse.success("Xóa tài khoản thành công", null);
    }

    @PatchMapping("/{username}/toggle-status")
    public apiResponse<AccountResponse> toggle(@PathVariable String username, @Valid @RequestBody StatusToggleRequest request, Authentication authentication) {
        return apiResponse.success("Đổi trạng thái tài khoản thành công", service.toggleStatus(username, request, authentication));
    }
}
