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
import phucuong.ht.common.exception.ApiException;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
        org.mockito.Mockito.lenient().when(validation.forbidden(anyString())).thenAnswer(invocation -> new ApiException(
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

    // ---- Đặt lại mật khẩu

    @Test
    void requireCanResetPasswordShouldRejectResettingOwnAccount() {
        actor("admin-diocese", "ADMIN_DIOCESE", AccountManagementAuthorizationHelper.RESET_PASSWORD_DIOCESE);

        assertThrows(ApiException.class, () -> helper.requireCanResetPassword(authentication, account("admin-diocese")));
    }

    @Test
    void requireCanResetPasswordShouldRejectSuperAdminTarget() {
        actor("admin-diocese", "ADMIN_DIOCESE", AccountManagementAuthorizationHelper.RESET_PASSWORD_DIOCESE);
        when(accountRoleRepository.findActiveRoleCodesByUsername("root")).thenReturn(List.of("SUPER_ADMIN"));

        assertThrows(ApiException.class, () -> helper.requireCanResetPassword(authentication, account("root")));
    }

    @Test
    void requireCanResetPasswordShouldNotBeGrantedByManagePermissionAlone() {
        UUID dioceseId = UUID.randomUUID();
        actor("admin-diocese", "ADMIN_DIOCESE", AccountManagementAuthorizationHelper.MANAGE_DIOCESE);
        Account target = accountInDiocese("deanery-admin", dioceseId);
        when(accountRoleRepository.findActiveByUsername("deanery-admin")).thenReturn(List.of(accountRole(role("ADMIN_DEANERY", 200))));

        assertThrows(ApiException.class, () -> helper.requireCanResetPassword(authentication, target));
    }

    @Test
    void requireCanResetPasswordShouldAllowLowerRoleInsideOwnDiocese() {
        UUID dioceseId = UUID.randomUUID();
        actor("admin-diocese", "ADMIN_DIOCESE", AccountManagementAuthorizationHelper.RESET_PASSWORD_DIOCESE);
        Account target = accountInDiocese("deanery-admin", dioceseId);
        when(accountRoleRepository.findActiveByUsername("deanery-admin")).thenReturn(List.of(accountRole(role("ADMIN_DEANERY", 200))));
        when(accountRoleRepository.findActiveByUsername("admin-diocese")).thenReturn(List.of(accountRole(role("ADMIN_DIOCESE", 100))));
        when(accountRepository.findActiveDioceseIdByUsername("admin-diocese")).thenReturn(java.util.Optional.of(dioceseId));

        assertDoesNotThrow(() -> helper.requireCanResetPassword(authentication, target));
    }

    @Test
    void requireCanResetPasswordShouldRejectTargetOutsideOwnDiocese() {
        actor("admin-diocese", "ADMIN_DIOCESE", AccountManagementAuthorizationHelper.RESET_PASSWORD_DIOCESE);
        Account target = accountInDiocese("other-deanery-admin", UUID.randomUUID());
        when(accountRoleRepository.findActiveByUsername("other-deanery-admin")).thenReturn(List.of(accountRole(role("ADMIN_DEANERY", 200))));
        when(accountRepository.findActiveDioceseIdByUsername("admin-diocese")).thenReturn(java.util.Optional.of(UUID.randomUUID()));

        assertThrows(ApiException.class, () -> helper.requireCanResetPassword(authentication, target));
    }

    @Test
    void requireCanResetPasswordShouldRejectTargetWithHigherRole() {
        UUID dioceseId = UUID.randomUUID();
        actor("deanery-admin", "ADMIN_DEANERY", AccountManagementAuthorizationHelper.RESET_PASSWORD_DIOCESE);
        Account target = accountInDiocese("diocese-admin", dioceseId);
        when(accountRoleRepository.findActiveByUsername("diocese-admin")).thenReturn(List.of(accountRole(role("ADMIN_DIOCESE", 100))));
        when(accountRoleRepository.findActiveByUsername("deanery-admin")).thenReturn(List.of(accountRole(role("ADMIN_DEANERY", 200))));
        when(accountRepository.findActiveDioceseIdByUsername("deanery-admin")).thenReturn(java.util.Optional.of(dioceseId));

        assertThrows(ApiException.class, () -> helper.requireCanResetPassword(authentication, target));
    }

    @Test
    void requireCanResetPasswordShouldRequireResetAllAndSuperAdminOwnershipForSuperAdmin() {
        actor("root", "SUPER_ADMIN", AccountManagementAuthorizationHelper.RESET_PASSWORD_ALL);
        Account owned = account("phucuongadmin");
        owned.setCreatedBy("super-admin");
        Account notOwned = account("someone");
        notOwned.setCreatedBy("another-admin");

        assertDoesNotThrow(() -> helper.requireCanResetPassword(authentication, owned));
        assertThrows(ApiException.class, () -> helper.requireCanResetPassword(authentication, notOwned));
    }

    @Test
    void requireCanResetPasswordShouldRejectSuperAdminWithoutResetAllPermission() {
        actor("root", "SUPER_ADMIN", AccountManagementAuthorizationHelper.MANAGE_ALL);
        Account owned = account("phucuongadmin");
        owned.setCreatedBy("super-admin");

        assertThrows(ApiException.class, () -> helper.requireCanResetPassword(authentication, owned));
    }

    private void actor(String username, String roleCode, String permission) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, null, List.of(roleCode), List.of(permission));
        org.mockito.Mockito.lenient().when(authentication.getPrincipal()).thenReturn(principal);
        org.mockito.Mockito.lenient().when(authentication.getAuthorities()).thenAnswer(invocation -> principal.getAuthorities());
    }

    private Account accountInDiocese(String username, UUID dioceseId) {
        Account target = account(username);
        Diocese diocese = new Diocese();
        diocese.setId(dioceseId);
        target.setDiocese(diocese);
        return target;
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
