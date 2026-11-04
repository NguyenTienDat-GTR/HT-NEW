package phucuong.ht.modules.system.controller;

import phucuong.ht.modules.privacy.model.PrivacyConsentSource;
import phucuong.ht.modules.privacy.service.PrivacyConsentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.system.model.request.AccountCreateRequest;
import phucuong.ht.modules.system.model.response.AccountPasswordResetResponse;
import phucuong.ht.modules.system.model.response.AccountResponse;
import phucuong.ht.modules.system.service.AccountManagementService;

import java.util.List;

@RestController
@RequestMapping("/api/system/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final PrivacyConsentService privacyConsent;
    private final AccountManagementService service;

    @PostMapping
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account.manage.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.parish')")
    public apiResponse<AccountResponse> create(@Valid @RequestBody AccountCreateRequest request, Authentication authentication) {
        AccountResponse created = service.create(request, authentication);
        privacyConsent.recordAttestation(authentication, null, PrivacyConsentSource.ACCOUNT_FORM);
        return apiResponse.success("Tạo tài khoản thành công", created);
    }

    @GetMapping
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account.manage.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.parish')")
    public apiResponse<Page<AccountResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lây sdanh sách tài khoản thành vông", service.findAll(request, authentication));
    }

    @GetMapping("/permission-targets")
    @PreAuthorize("@systemAdminAuthorizationHelper.isSuperAdmin(authentication)"
            + " || @securityUtils.hasAuthority(authentication, 'system.account_permission.manage.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account_permission.manage.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account_permission.manage.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account_permission.manage.parish')")
    public apiResponse<List<AccountResponse>> permissionTargets(
            @RequestParam(required = false) String search,
            Authentication authentication
    ) {
        return apiResponse.success("Lấy danh sách tài khoản có thể phân quyền thành công", service.findAccountPermissionTargets(authentication, search));
    }

    @GetMapping("/{username}")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account.manage.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.parish')")
    public apiResponse<AccountResponse> findById(@PathVariable String username, Authentication authentication) {
        return apiResponse.success("Lấy thông tin tài khoản thành công", service.findById(username, authentication));
    }

    @PostMapping("/{username}/reset-password")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account.reset_password.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.reset_password.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.reset_password.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.reset_password.parish')")
    public apiResponse<AccountPasswordResetResponse> resetPassword(@PathVariable String username, Authentication authentication) {
        AccountPasswordResetResponse response = service.resetPassword(username, authentication);
        return apiResponse.success("Đã gửi mật khẩu tạm tới " + response.getMaskedEmail(), response);
    }

    @PatchMapping("/{username}/toggle-status")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account.manage.all')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.diocese')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.deanery')"
            + " || @securityUtils.hasAuthority(authentication, 'system.account.manage.parish')")
    public apiResponse<AccountResponse> toggle(@PathVariable String username, @Valid @RequestBody StatusToggleRequest request, Authentication authentication) {
        return apiResponse.success("Đổi trạng thái tài khoản thành công", service.toggleStatus(username, request, authentication));
    }
}
