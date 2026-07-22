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
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountRole;
import phucuong.ht.modules.system.model.entity.Role;

import java.util.List;
import java.util.UUID;

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

    @Test
    void requireCanManageShouldRejectTargetWithHigherRealRoleEvenWhenActorHasWideAuthority() {
        UUID dioceseId = UUID.randomUUID();
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "datbung107",
                null,
                List.of("ADMIN_DEANERY"),
                List.of(AccountManagementAuthorizationHelper.MANAGE_DIOCESE)
        );
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authentication.getAuthorities()).thenAnswer(invocation -> principal.getAuthorities());
        Account target = account("phucuongadmin");
        Diocese diocese = new Diocese();
        diocese.setId(dioceseId);
        target.setDiocese(diocese);

        when(accountRoleRepository.findActiveRoleCodesByUsername("phucuongadmin")).thenReturn(List.of("ADMIN_DIOCESE"));
        when(accountRoleRepository.findActiveByUsername("phucuongadmin")).thenReturn(List.of(accountRole(role("ADMIN_DIOCESE", 100))));
        when(accountRoleRepository.findActiveByUsername("datbung107")).thenReturn(List.of(accountRole(role("ADMIN_DEANERY", 200))));
        when(accountRepository.findActiveDioceseIdByUsername("datbung107")).thenReturn(java.util.Optional.of(dioceseId));

        assertThrows(ApiException.class, () -> helper.requireCanManage(authentication, target));
    }

    private Account account(String username) {
        Account account = new Account();
        account.setUsername(username);
        return account;
    }

    private AccountRole accountRole(Role role) {
        AccountRole accountRole = new AccountRole();
        accountRole.setRole(role);
        accountRole.setStatus(true);
        return accountRole;
    }

    private Role role(String roleCode, int displayOrder) {
        Role role = new Role();
        role.setRoleCode(roleCode);
        role.setDisplayOrder(displayOrder);
        role.setStatus(true);
        return role;
    }
}
