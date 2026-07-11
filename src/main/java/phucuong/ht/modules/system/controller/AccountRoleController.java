package phucuong.ht.modules.system.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.system.model.request.AccountRoleCreateRequest;
import phucuong.ht.modules.system.model.request.AccountRoleUpdateRequest;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.modules.system.model.response.AccountRoleResponse;
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
        return apiResponse.success("Gán role thành công", accountRoleService.assignRole(request, authentication));
    }
    @GetMapping
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account_role.assign.all') || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.diocese') || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.deanery') || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.parish')")
    public apiResponse<List<AccountRoleResponse>> findAll(Authentication authentication) { return apiResponse.success("Lấy danh sách role tài khoản thành công", accountRoleService.findAll(authentication)); }

    @GetMapping("/{id}")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account_role.assign.all') || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.diocese') || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.deanery') || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.parish')")
    public apiResponse<AccountRoleResponse> findById(@PathVariable String id, Authentication authentication) { return apiResponse.success("Lấy chi tiết role tài khoản thành công", accountRoleService.findById(id, authentication)); }

    @PutMapping("/{id}")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account_role.assign.all') || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.diocese') || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.deanery') || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.parish')")
    public apiResponse<AccountRoleResponse> update(@PathVariable String id, @RequestBody AccountRoleUpdateRequest request, Authentication authentication) { return apiResponse.success("Cập nhật role tài khoản thành công", accountRoleService.update(id, request, authentication)); }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account_role.assign.all') || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.diocese') || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.deanery') || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.parish')")
    public apiResponse<Void> delete(@PathVariable String id, Authentication authentication) { accountRoleService.delete(id, authentication); return apiResponse.success("Xóa role tài khoản thành công", null); }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("@securityUtils.hasAuthority(authentication, 'system.account_role.assign.all') || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.diocese') || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.deanery') || @securityUtils.hasAuthority(authentication, 'system.account_role.assign.parish')")
    public apiResponse<AccountRoleResponse> toggle(@PathVariable String id, @Valid @RequestBody StatusToggleRequest request, Authentication authentication) { return apiResponse.success("Đổi trạng thái role tài khoản thành công", accountRoleService.toggleStatus(id, request, authentication)); }
}
