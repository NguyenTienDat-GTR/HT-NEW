package phucuong.ht.modules.system.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.system.model.request.AccountCreateRequest;
import phucuong.ht.modules.system.model.request.AccountUpdateRequest;
import phucuong.ht.modules.system.model.response.AccountResponse;
import phucuong.ht.modules.system.service.AccountManagementService;

import java.util.List;

@RestController
@RequestMapping("/api/system/accounts")
@RequiredArgsConstructor
public class AccountController {
    private final AccountManagementService service;

    @PostMapping
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account.manage.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.parish')")
    public apiResponse<AccountResponse> create(@Valid @RequestBody AccountCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tao tai khoan thanh cong", service.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account.manage.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.parish')")
    public apiResponse<Page<AccountResponse>> findAll(@ModelAttribute BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lay danh sach tai khoan thanh cong", service.findAll(request, authentication));
    }

    @GetMapping("/permission-targets")
    @PreAuthorize("@systemAdminAuthorizationHelper.isSuperAdmin(authentication)"
            + " || @securityUtils.hasAuthority(authentication, 'system.account_permission.manage.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account_permission.manage.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account_permission.manage.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account_permission.manage.parish')")
    public apiResponse<List<AccountResponse>> permissionTargets(Authentication authentication) {
        return apiResponse.success("Lay danh sach tai khoan co the phan quyen thanh cong", service.findAccountPermissionTargets(authentication));
    }

    @GetMapping("/{username}")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account.manage.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.parish')")
    public apiResponse<AccountResponse> findById(@PathVariable String username, Authentication authentication) {
        return apiResponse.success("Lay thong tin tai khoan thanh cong", service.findById(username, authentication));
    }

    @PutMapping("/{username}")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account.manage.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.parish')")
    public apiResponse<AccountResponse> update(@PathVariable String username, @RequestBody AccountUpdateRequest request, Authentication authentication) {
        return apiResponse.success("Cap nhat tai khoan thanh cong", service.update(username, request, authentication));
    }

    @DeleteMapping("/{username}")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account.manage.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.parish')")
    public apiResponse<Void> delete(@PathVariable String username, Authentication authentication) {
        service.delete(username, authentication);
        return apiResponse.success("Xoa tai khoan thanh cong", null);
    }

    @PatchMapping("/{username}/toggle-status")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account.manage.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.parish')")
    public apiResponse<AccountResponse> toggle(@PathVariable String username, @Valid @RequestBody StatusToggleRequest request, Authentication authentication) {
        return apiResponse.success("Doi trang thai tai khoan thanh cong", service.toggleStatus(username, request, authentication));
    }
}
