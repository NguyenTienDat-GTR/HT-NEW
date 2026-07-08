package phucuong.ht.modules.system.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.system.model.request.AccountRoleCreateRequest;
import phucuong.ht.modules.system.model.response.AccountRoleResponse;
import phucuong.ht.modules.system.service.AccountRoleService;

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
    )
    public apiResponse<AccountRoleResponse> assignRole(
            @Valid @RequestBody AccountRoleCreateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Gán role thành công", accountRoleService.assignRole(request, authentication));
    }
}
