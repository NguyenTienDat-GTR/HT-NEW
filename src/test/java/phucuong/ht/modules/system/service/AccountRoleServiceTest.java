package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.AccountRole;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.request.AccountRoleBulkRequest;
import phucuong.ht.modules.system.model.response.AccountRoleResponse;
import phucuong.ht.modules.system.model.response.RoleResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountRoleServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountRoleRepository accountRoleRepository;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private AccountRoleService accountRoleService;

    @Test
    void superAdminCanBulkAssignRoleToManagedAdminDioceseAccount() {
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, new CommonValidationHelper(new securityUtils()));
        Account account = dioceseAccount("diocese-admin");
        Role targetRole = role("ADMIN_DIOCESE", true, 100, "SYSTEM");
        AccountRoleBulkRequest request = new AccountRoleBulkRequest();
        request.setUsername("diocese-admin");
        request.setRoleCodes(List.of("ADMIN_DIOCESE"));
        request.setPrimaryRoleCode("ADMIN_DIOCESE");

        when(accountRepository.findById("diocese-admin")).thenReturn(Optional.of(account));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DIOCESE")).thenReturn(Optional.of(targetRole));
        when(accountRoleRepository.findByAccountUsernameAndRoleRoleCode("diocese-admin", "ADMIN_DIOCESE")).thenReturn(Optional.empty());
        when(accountRoleRepository.save(any(AccountRole.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRoleRepository.findByAccountUsername("diocese-admin")).thenReturn(List.of());

        List<AccountRoleResponse> responses = accountRoleService.bulkAssign(request, authentication("super-admin", List.of("SUPER_ADMIN"), List.of("system.account_role.assign.all")));

        assertEquals(1, responses.size());
        assertEquals("diocese-admin", responses.get(0).getUsername());
        assertEquals("ADMIN_DIOCESE", responses.get(0).getRoleCode());
        assertEquals(true, responses.get(0).getIsPrimary());
    }

    @Test
    void superAdminCannotAssignSystemRoleOutsideManagedScope() {
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, new CommonValidationHelper(new securityUtils()));
        Account account = dioceseAccount("diocese-admin");
        Role targetRole = role("ADMIN_DEANERY", true, 200, "SYSTEM");
        AccountRoleBulkRequest request = new AccountRoleBulkRequest();
        request.setUsername("diocese-admin");
        request.setRoleCodes(List.of("ADMIN_DEANERY"));

        when(accountRepository.findById("diocese-admin")).thenReturn(Optional.of(account));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DEANERY")).thenReturn(Optional.of(targetRole));

        assertThrows(RuntimeException.class, () -> accountRoleService.bulkAssign(request, authentication("super-admin", List.of("SUPER_ADMIN"), List.of("system.account_role.assign.all"))));
    }

    @Test
    void superAdminCanToggleInactiveAdminDioceseAssignment() {
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, new CommonValidationHelper(new securityUtils()));
        Account account = dioceseAccount("diocese-admin");
        Role targetRole = role("ADMIN_DIOCESE", true, 100, "SYSTEM");
        AccountRole accountRole = new AccountRole();
        accountRole.setId("ar-1");
        accountRole.setAccount(account);
        accountRole.setRole(targetRole);
        accountRole.setStatus(false);

        StatusToggleRequest request = new StatusToggleRequest();
        request.setConfirmationText("Xác nhận");

        when(accountRoleRepository.findById("ar-1")).thenReturn(Optional.of(accountRole));

        AccountRoleResponse response = accountRoleService.toggleStatus(
                "ar-1",
                request,
                authentication("super-admin", List.of("SUPER_ADMIN"), List.of("system.account_role.assign.all"))
        );

        assertEquals(true, response.getStatus());
        assertEquals("ADMIN_DIOCESE", response.getRoleCode());
    }

    @Test
    void adminDioceseCanBulkAssignAdminDeaneryRoleToAccountInOwnDiocese() {
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, new CommonValidationHelper(new securityUtils()));
        UUID dioceseId = UUID.randomUUID();
        Account account = dioceseAccount("target", dioceseId);
        Role targetRole = role("ADMIN_DEANERY", true, 200, "SYSTEM");
        Role actorRole = role("ADMIN_DIOCESE", true, 100, "SYSTEM");
        AccountRoleBulkRequest request = new AccountRoleBulkRequest();
        request.setUsername("target");
        request.setRoleCodes(List.of("ADMIN_DEANERY"));

        when(accountRepository.findById("target")).thenReturn(Optional.of(account));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DEANERY")).thenReturn(Optional.of(targetRole));
        when(accountRepository.findActiveDioceseIdByUsername("admin-diocese")).thenReturn(Optional.of(dioceseId));
        when(accountRepository.findActiveDioceseIdByUsername("target")).thenReturn(Optional.of(dioceseId));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DIOCESE")).thenReturn(Optional.of(actorRole));
        when(accountRoleRepository.findByAccountUsernameAndRoleRoleCode("target", "ADMIN_DEANERY")).thenReturn(Optional.empty());
        when(accountRoleRepository.save(any(AccountRole.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<AccountRoleResponse> responses = accountRoleService.bulkAssign(
                request,
                authentication("admin-diocese", List.of("ADMIN_DIOCESE"), List.of("system.account_role.assign.diocese"))
        );

        assertEquals(1, responses.size());
        assertEquals("ADMIN_DEANERY", responses.get(0).getRoleCode());
    }

    @Test
    void adminDioceseAssignableRolesIncludeAdminDeaneryAndAdminParishButNotAdminDiocese() {
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, new CommonValidationHelper(new securityUtils()));
        UUID dioceseId = UUID.randomUUID();
        Account account = dioceseAccount("target", dioceseId);
        Role adminDiocese = role("ADMIN_DIOCESE", true, 100, "SYSTEM");
        Role adminDeanery = role("ADMIN_DEANERY", true, 200, "SYSTEM");
        Role adminParish = role("ADMIN_PARISH", true, 300, "SYSTEM");

        when(accountRepository.findById("target")).thenReturn(Optional.of(account));
        when(accountRoleRepository.findByAccountUsername("target")).thenReturn(List.of());
        when(roleRepository.findAll()).thenReturn(List.of(adminDiocese, adminDeanery, adminParish));
        when(accountRepository.findActiveDioceseIdByUsername("admin-diocese")).thenReturn(Optional.of(dioceseId));
        when(accountRepository.findActiveDioceseIdByUsername("target")).thenReturn(Optional.of(dioceseId));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DIOCESE")).thenReturn(Optional.of(adminDiocese));

        List<String> roleCodes = accountRoleService.findAssignableRoles(
                        "target",
                        authentication("admin-diocese", List.of("ADMIN_DIOCESE"), List.of("system.account_role.assign.diocese"))
                ).stream()
                .map(RoleResponse::getRoleCode)
                .toList();

        assertEquals(List.of("ADMIN_DEANERY", "ADMIN_PARISH"), roleCodes);
    }

    @Test
    void adminDioceseCannotAssignAdminDioceseRoleToAccount() {
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, new CommonValidationHelper(new securityUtils()));
        Account account = dioceseAccount("target");
        Role targetRole = role("ADMIN_DIOCESE", true, 100, "SYSTEM");
        AccountRoleBulkRequest request = new AccountRoleBulkRequest();
        request.setUsername("target");
        request.setRoleCodes(List.of("ADMIN_DIOCESE"));

        when(accountRepository.findById("target")).thenReturn(Optional.of(account));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DIOCESE")).thenReturn(Optional.of(targetRole));

        assertThrows(RuntimeException.class, () -> accountRoleService.bulkAssign(
                request,
                authentication("admin-diocese", List.of("ADMIN_DIOCESE"), List.of("system.account_role.assign.diocese"))
        ));
    }

    private Account dioceseAccount(String username) {
        return dioceseAccount(username, UUID.randomUUID());
    }

    private Account dioceseAccount(String username, UUID dioceseId) {
        Diocese diocese = new Diocese();
        diocese.setId(dioceseId);
        Account account = new Account();
        account.setUsername(username);
        account.setDiocese(diocese);
        account.setStatus(true);
        return account;
    }

    private Role role(String roleCode, boolean system, int displayOrder, String createdBy) {
        Role role = new Role();
        role.setRoleCode(roleCode);
        role.setRoleName(roleCode);
        role.setIsSystem(system);
        role.setDisplayOrder(displayOrder);
        role.setCreatedBy(createdBy);
        role.setStatus(true);
        return role;
    }

    private UsernamePasswordAuthenticationToken authentication(String username, List<String> roles, List<String> permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, null, roles, permissions);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
