package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountRole;
import phucuong.ht.modules.system.model.entity.Permission;
import phucuong.ht.modules.system.model.entity.PermissionScope;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.enums.PermissionEffect;
import phucuong.ht.modules.system.model.request.AccountPermissionOverrideCreateRequest;
import phucuong.ht.modules.system.repository.AccountPermissionOverrideRepository;
import phucuong.ht.modules.system.repository.RolePermissionRepository;
import phucuong.ht.modules.system.repository.SystemPermissionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountPermissionOverrideManagementServiceTest {

    @Mock
    private AccountPermissionOverrideRepository accountPermissionOverrideRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountRoleRepository accountRoleRepository;

    @Mock
    private SystemPermissionRepository systemPermissionRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private AccountScopeValidationHelper accountScopeValidationHelper;

    @Mock
    private RoleRepository roleRepository;

    @Test
    void createShouldRejectDioceseAdminAssigningAllScopePermission() {
        CommonValidationHelper helper = new CommonValidationHelper(new securityUtils());
        SystemAdminAuthorizationHelper authorizationHelper = new SystemAdminAuthorizationHelper(
                new securityUtils(),
                accountScopeValidationHelper,
                accountRepository,
                roleRepository,
                helper
        );
        AccountPermissionOverrideManagementService service = new AccountPermissionOverrideManagementService(
                accountPermissionOverrideRepository,
                accountRepository,
                accountRoleRepository,
                systemPermissionRepository,
                rolePermissionRepository,
                helper,
                authorizationHelper
        );

        Account account = new Account();
        account.setUsername("target");
        account.setStatus(true);

        PermissionScope scope = new PermissionScope();
        scope.setScopeCode("all");
        Permission permission = new Permission();
        permission.setPermissionCode("system.permission.read.all");
        permission.setScope(scope);

        AccountPermissionOverrideCreateRequest request = new AccountPermissionOverrideCreateRequest();
        request.setUsername("target");
        request.setPermissionCode("system.permission.read.all");
        request.setEffect(PermissionEffect.DENY);

        when(accountRepository.findActiveByUsername("target")).thenReturn(Optional.of(account));
        when(systemPermissionRepository.findById("system.permission.read.all")).thenReturn(Optional.of(permission));

        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of("system.account_permission.manage.diocese")
        );
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        assertThrows(RuntimeException.class, () -> service.create(request, authentication));
    }

    @Test
    void createShouldRejectDioceseAdminManagingDifferentDioceseAccount() {
        CommonValidationHelper helper = new CommonValidationHelper(new securityUtils());
        SystemAdminAuthorizationHelper authorizationHelper = new SystemAdminAuthorizationHelper(
                new securityUtils(),
                accountScopeValidationHelper,
                accountRepository,
                roleRepository,
                helper
        );
        AccountPermissionOverrideManagementService service = new AccountPermissionOverrideManagementService(
                accountPermissionOverrideRepository,
                accountRepository,
                accountRoleRepository,
                systemPermissionRepository,
                rolePermissionRepository,
                helper,
                authorizationHelper
        );

        Account account = new Account();
        account.setUsername("target");
        account.setStatus(true);

        PermissionScope scope = new PermissionScope();
        scope.setScopeCode("diocese");
        Permission permission = new Permission();
        permission.setPermissionCode("organization.position.read.diocese");
        permission.setScope(scope);

        AccountPermissionOverrideCreateRequest request = new AccountPermissionOverrideCreateRequest();
        request.setUsername("target");
        request.setPermissionCode("organization.position.read.diocese");
        request.setEffect(PermissionEffect.DENY);

        when(accountRepository.findActiveByUsername("target")).thenReturn(Optional.of(account));
        when(systemPermissionRepository.findById("organization.position.read.diocese")).thenReturn(Optional.of(permission));
        when(accountScopeValidationHelper.requireActiveDioceseId(authentication(), "system.account_permission.manage.diocese"))
                .thenReturn(UUID.randomUUID());
        when(accountRepository.findActiveDioceseIdByUsername("target")).thenReturn(Optional.of(UUID.randomUUID()));

        assertThrows(RuntimeException.class, () -> service.create(request, authentication()));
    }

    @Test
    void createShouldRejectDioceseAdminAssigningPermissionItDoesNotOwn() {
        CommonValidationHelper helper = new CommonValidationHelper(new securityUtils());
        SystemAdminAuthorizationHelper authorizationHelper = new SystemAdminAuthorizationHelper(
                new securityUtils(),
                accountScopeValidationHelper,
                accountRepository,
                roleRepository,
                helper
        );
        AccountPermissionOverrideManagementService service = new AccountPermissionOverrideManagementService(
                accountPermissionOverrideRepository,
                accountRepository,
                accountRoleRepository,
                systemPermissionRepository,
                rolePermissionRepository,
                helper,
                authorizationHelper
        );

        Account account = new Account();
        account.setUsername("target");
        account.setStatus(true);

        PermissionScope scope = new PermissionScope();
        scope.setScopeCode("diocese");
        Permission permission = new Permission();
        permission.setPermissionCode("system.role_permission.assign.diocese");
        permission.setScope(scope);
        permission.setStatus(true);

        AccountPermissionOverrideCreateRequest request = new AccountPermissionOverrideCreateRequest();
        request.setUsername("target");
        request.setPermissionCode("system.role_permission.assign.diocese");
        request.setEffect(PermissionEffect.ALLOW);

        when(accountRepository.findActiveByUsername("target")).thenReturn(Optional.of(account));
        when(systemPermissionRepository.findById("system.role_permission.assign.diocese")).thenReturn(Optional.of(permission));

        assertThrows(RuntimeException.class, () -> service.create(request, authentication()));
    }

    @Test
    void createShouldRejectDenyingRequiredAccountRoleAssignmentPermissionForAdminUnitAccount() {
        CommonValidationHelper helper = new CommonValidationHelper(new securityUtils());
        SystemAdminAuthorizationHelper authorizationHelper = new SystemAdminAuthorizationHelper(
                new securityUtils(),
                accountScopeValidationHelper,
                accountRepository,
                roleRepository,
                helper
        );
        AccountPermissionOverrideManagementService service = new AccountPermissionOverrideManagementService(
                accountPermissionOverrideRepository,
                accountRepository,
                accountRoleRepository,
                systemPermissionRepository,
                rolePermissionRepository,
                helper,
                authorizationHelper
        );

        Account account = new Account();
        account.setUsername("phucuongadmin");
        account.setStatus(true);

        Role role = new Role();
        role.setRoleCode("ADMIN_DIOCESE");
        role.setIsSystem(true);
        AccountRole accountRole = new AccountRole();
        accountRole.setAccount(account);
        accountRole.setRole(role);

        PermissionScope scope = new PermissionScope();
        scope.setScopeCode("diocese");
        Permission permission = new Permission();
        permission.setPermissionCode("system.account_role.assign.diocese");
        permission.setScope(scope);
        permission.setStatus(true);

        AccountPermissionOverrideCreateRequest request = new AccountPermissionOverrideCreateRequest();
        request.setUsername("phucuongadmin");
        request.setPermissionCode("system.account_role.assign.diocese");
        request.setEffect(PermissionEffect.DENY);

        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "superadminpc",
                null,
                List.of("SUPER_ADMIN"),
                List.of("system.account_permission.manage.all")
        );
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(accountRepository.findActiveByUsername("phucuongadmin")).thenReturn(Optional.of(account));
        when(systemPermissionRepository.findById("system.account_role.assign.diocese")).thenReturn(Optional.of(permission));
        when(accountRoleRepository.findActiveByUsername("phucuongadmin")).thenReturn(List.of(accountRole));

        assertThrows(RuntimeException.class, () -> service.create(request, authentication));
    }

    private UsernamePasswordAuthenticationToken authentication() {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of("system.account_permission.manage.diocese")
        );
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
