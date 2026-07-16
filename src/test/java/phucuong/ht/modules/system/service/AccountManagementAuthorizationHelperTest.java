package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Account;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountManagementAuthorizationHelperTest {

    @Mock AccountRepository accountRepository;
    @Mock AccountRoleRepository accountRoleRepository;
    @Mock RoleRepository roleRepository;
    @Mock CommonValidationHelper validation;
    @Mock Authentication authentication;

    AccountManagementAuthorizationHelper helper;

    @BeforeEach
    void setUp() {
        helper = new AccountManagementAuthorizationHelper(accountRepository, accountRoleRepository, roleRepository, validation);
        when(validation.forbidden(anyString())).thenAnswer(invocation -> new ApiException(
                HttpStatus.FORBIDDEN,
                errorCode.ACCESS_DENIED,
                invocation.getArgument(0)
        ));
    }

    @Test
    void requireCanManageShouldRejectCurrentAccountForAnyRole() {
        when(authentication.getPrincipal()).thenReturn(AuthUserDetails.tokenPrincipal(
                "admin-diocese",
                null,
                List.of("ADMIN_DIOCESE"),
                List.of(AccountManagementAuthorizationHelper.MANAGE_DIOCESE)
        ));
        Account target = account("admin-diocese");

        assertThrows(ApiException.class, () -> helper.requireCanManage(authentication, target));

        verify(accountRoleRepository, never()).findActiveRoleCodesByUsername(anyString());
    }

    @Test
    void requireCanManageShouldRejectSuperAdminTargetAccount() {
        when(authentication.getPrincipal()).thenReturn(AuthUserDetails.tokenPrincipal(
                "root",
                null,
                List.of("SUPER_ADMIN"),
                List.of(AccountManagementAuthorizationHelper.MANAGE_ALL)
        ));
        Account target = account("other-root");
        when(accountRoleRepository.findActiveRoleCodesByUsername("other-root")).thenReturn(List.of("SUPER_ADMIN"));

        assertThrows(ApiException.class, () -> helper.requireCanManage(authentication, target));
    }

    private Account account(String username) {
        Account account = new Account();
        account.setUsername(username);
        return account;
    }
}
