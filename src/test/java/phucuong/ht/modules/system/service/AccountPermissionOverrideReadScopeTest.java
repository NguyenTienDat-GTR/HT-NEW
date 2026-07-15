package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountPermissionOverride;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.repository.AccountPermissionOverrideRepository;
import phucuong.ht.modules.system.repository.SystemPermissionRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPermissionOverrideReadScopeTest {

    @Mock
    private AccountPermissionOverrideRepository overrideRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountRoleRepository accountRoleRepository;

    @Mock
    private SystemPermissionRepository permissionRepository;

    @Mock
    private SystemAdminAuthorizationHelper authorizationHelper;

    @Mock
    private Authentication authentication;

    @Test
    void shouldFilterScopeBeforeApplyingRequestedPagination() {
        AccountPermissionOverride outside = override("outside", "permission.outside");
        AccountPermissionOverride inside = override("inside", "permission.inside");
        when(overrideRepository.findAll(any(BaseSearchRequest.class)))
                .thenReturn(new PageImpl<>(List.of(outside, inside), PageRequest.of(0, BaseSearchRequest.MAX_PAGE_SIZE), 2));
        when(authorizationHelper.canManageAccountPermission(eq(authentication), eq("outside"), any(Permission.class))).thenReturn(false);
        when(authorizationHelper.canManageAccountPermission(eq(authentication), eq("inside"), any(Permission.class))).thenReturn(true);

        BaseSearchRequest request = new BaseSearchRequest();
        request.setSize(1);
        var response = service().findAll(request, authentication);

        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());
        assertEquals("inside", response.getContent().getFirst().getUsername());
    }

    @Test
    void shouldReturnForbiddenForOutOfScopeDetail() {
        AccountPermissionOverride outside = override("outside", "permission.outside");
        when(overrideRepository.findById("override-1")).thenReturn(Optional.of(outside));
        when(authorizationHelper.canManageAccountPermission(eq(authentication), eq("outside"), any(Permission.class))).thenReturn(false);

        assertThrows(RuntimeException.class, () -> service().findById("override-1", authentication));
    }

    private AccountPermissionOverrideManagementService service() {
        return new AccountPermissionOverrideManagementService(
                overrideRepository,
                accountRepository,
                accountRoleRepository,
                permissionRepository,
                new CommonValidationHelper(new securityUtils()),
                authorizationHelper
        );
    }

    private AccountPermissionOverride override(String username, String permissionCode) {
        Account account = new Account();
        account.setUsername(username);
        Permission permission = new Permission();
        permission.setPermissionCode(permissionCode);
        AccountPermissionOverride override = new AccountPermissionOverride();
        override.setId("override-1");
        override.setAccount(account);
        override.setPermission(permission);
        return override;
    }
}
