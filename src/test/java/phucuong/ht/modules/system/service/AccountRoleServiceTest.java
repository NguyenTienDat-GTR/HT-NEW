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
import phucuong.ht.modules.system.model.request.AccountRoleUpdateRequest;
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

    @Mock
    private AccountManagementAuthorizationHelper accountManagementAuthorizationHelper;

    @org.mockito.Mock
    private phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;

    @InjectMocks
    private AccountRoleService accountRoleService;

    @Test
    void superAdminCanBulkAssignRoleToManagedAdminDioceseAccount() {
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, new CommonValidationHelper(new securityUtils()), accountManagementAuthorizationHelper, org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
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
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, new CommonValidationHelper(new securityUtils()), accountManagementAuthorizationHelper, org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
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
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, new CommonValidationHelper(new securityUtils()), accountManagementAuthorizationHelper, org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
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
    void disablingPrimaryRoleShouldPromoteHighestActiveRemainingRole() {
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, new CommonValidationHelper(new securityUtils()), accountManagementAuthorizationHelper, org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
        Account account = dioceseAccount("diocese-admin");
        AccountRole primary = accountRole(account, role("ADMIN_DIOCESE", true, 100, "SYSTEM"));
        primary.setId("ar-primary");
        primary.setIsPrimary(true);
        AccountRole highestRemaining = accountRole(account, role("CUSTOM_HIGH", false, 150, "super-admin"));
        highestRemaining.setId("ar-high");
        highestRemaining.setIsPrimary(false);
        AccountRole lowerRemaining = accountRole(account, role("CUSTOM_LOW", false, 250, "super-admin"));
        lowerRemaining.setId("ar-low");
        lowerRemaining.setIsPrimary(false);

        StatusToggleRequest request = new StatusToggleRequest();
        request.setConfirmationText("Xác nhận");

        when(accountRoleRepository.findById("ar-primary")).thenReturn(Optional.of(primary));
        when(accountRoleRepository.findByAccountUsername("diocese-admin")).thenReturn(List.of(primary, highestRemaining, lowerRemaining));

        AccountRoleResponse response = accountRoleService.toggleStatus(
                "ar-primary",
                request,
                authentication("super-admin", List.of("SUPER_ADMIN"), List.of("system.account_role.assign.all"))
        );

        assertEquals(false, response.getStatus());
        assertEquals(false, primary.getIsPrimary());
        assertEquals(true, highestRemaining.getIsPrimary());
        assertEquals(false, lowerRemaining.getIsPrimary());
    }

    @Test
    void updatePrimaryRoleShouldClearExistingPrimaryBeforeSettingNewPrimary() {
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, new CommonValidationHelper(new securityUtils()), accountManagementAuthorizationHelper, org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
        Account account = dioceseAccount("diocese-admin");
        AccountRole target = accountRole(account, role("ADMIN_DIOCESE", true, 100, "SYSTEM"));
        target.setId("ar-target");
        target.setIsPrimary(false);
        AccountRole currentPrimary = accountRole(account, role("CUSTOM_ROLE", false, 200, "super-admin"));
        currentPrimary.setId("ar-current");
        currentPrimary.setIsPrimary(true);
        AccountRoleUpdateRequest request = new AccountRoleUpdateRequest();
        request.setIsPrimary(true);

        when(accountRoleRepository.findById("ar-target")).thenReturn(Optional.of(target));
        when(accountRoleRepository.findByAccountUsername("diocese-admin")).thenReturn(List.of(target, currentPrimary));

        AccountRoleResponse response = accountRoleService.update(
                "ar-target",
                request,
                authentication("super-admin", List.of("SUPER_ADMIN"), List.of("system.account_role.assign.all"))
        );

        assertEquals(true, response.getIsPrimary());
        assertEquals(true, target.getIsPrimary());
        assertEquals(false, currentPrimary.getIsPrimary());
        org.mockito.Mockito.verify(accountRoleRepository).flush();
    }

    @Test
    void adminDioceseCanBulkAssignAdminDeaneryRoleToAccountInOwnDiocese() {
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, new CommonValidationHelper(new securityUtils()), accountManagementAuthorizationHelper, org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
        UUID dioceseId = UUID.randomUUID();
        Account account = dioceseAccount("target", dioceseId);
        Role targetRole = role("ADMIN_DEANERY", true, 200, "SYSTEM");
        AccountRoleBulkRequest request = new AccountRoleBulkRequest();
        request.setUsername("target");
        request.setRoleCodes(List.of("ADMIN_DEANERY"));

        when(accountRepository.findById("target")).thenReturn(Optional.of(account));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_DEANERY")).thenReturn(Optional.of(targetRole));
        when(accountRepository.findActiveDioceseIdByUsername("admin-diocese")).thenReturn(Optional.of(dioceseId));
        when(accountRepository.findActiveDioceseIdByUsername("target")).thenReturn(Optional.of(dioceseId));
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
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, new CommonValidationHelper(new securityUtils()), accountManagementAuthorizationHelper, org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
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

        List<String> roleCodes = accountRoleService.findAssignableRoles(
                        "target",
                        authentication("admin-diocese", List.of("ADMIN_DIOCESE"), List.of("system.account_role.assign.diocese"))
                ).stream()
                .map(RoleResponse::getRoleCode)
                .toList();

        assertEquals(List.of("ADMIN_DEANERY", "ADMIN_PARISH"), roleCodes);
    }

    @Test
    void adminDeaneryCanBulkAssignAdminParishRoleToAccountInOwnDeanery() {
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, new CommonValidationHelper(new securityUtils()), accountManagementAuthorizationHelper, org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
        UUID deaneryId = UUID.randomUUID();
        Account account = dioceseAccount("target");
        Role targetRole = role("ADMIN_PARISH", true, 300, "SYSTEM");
        AccountRoleBulkRequest request = new AccountRoleBulkRequest();
        request.setUsername("target");
        request.setRoleCodes(List.of("ADMIN_PARISH"));

        when(accountRepository.findById("target")).thenReturn(Optional.of(account));
        when(roleRepository.findByRoleCodeAndStatusTrue("ADMIN_PARISH")).thenReturn(Optional.of(targetRole));
        when(accountRepository.findActiveDeaneryIdByUsername("admin-deanery")).thenReturn(Optional.of(deaneryId));
        when(accountRepository.findActiveDeaneryIdByUsername("target")).thenReturn(Optional.of(deaneryId));
        when(accountRoleRepository.findByAccountUsernameAndRoleRoleCode("target", "ADMIN_PARISH")).thenReturn(Optional.empty());
        when(accountRoleRepository.save(any(AccountRole.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<AccountRoleResponse> responses = accountRoleService.bulkAssign(
                request,
                authentication("admin-deanery", List.of("ADMIN_DEANERY"), List.of("system.account_role.assign.deanery"))
        );

        assertEquals(1, responses.size());
        assertEquals("ADMIN_PARISH", responses.get(0).getRoleCode());
    }

    @Test
    void adminDioceseCannotAssignAdminDioceseRoleToAccount() {
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, new CommonValidationHelper(new securityUtils()), accountManagementAuthorizationHelper, org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
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

    @Test
    void lowerRealRoleCannotAssignHigherCustomRoleEvenWithWideAuthority() {
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, new CommonValidationHelper(new securityUtils()), accountManagementAuthorizationHelper, org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
        UUID dioceseId = UUID.randomUUID();
        Account account = dioceseAccount("target", dioceseId);
        Role targetRole = role("CUSTOM_HIGH", false, 100, "super-admin");
        AccountRoleBulkRequest request = new AccountRoleBulkRequest();
        request.setUsername("target");
        request.setRoleCodes(List.of("CUSTOM_HIGH"));

        when(accountRepository.findById("target")).thenReturn(Optional.of(account));
        when(roleRepository.findByRoleCodeAndStatusTrue("CUSTOM_HIGH")).thenReturn(Optional.of(targetRole));
        when(accountRepository.findActiveDioceseIdByUsername("datbung107")).thenReturn(Optional.of(dioceseId));
        when(accountRepository.findActiveDioceseIdByUsername("target")).thenReturn(Optional.of(dioceseId));
        when(accountManagementAuthorizationHelper.currentActorHighestRoleOrder(any())).thenReturn(200);

        assertThrows(RuntimeException.class, () -> accountRoleService.bulkAssign(
                request,
                authentication("datbung107", List.of("ADMIN_DEANERY"), List.of("system.account_role.assign.diocese"))
        ));
    }

    @Test
    void lowerRealRoleCannotToggleAssignmentOnHigherRoleTargetAccount() {
        CommonValidationHelper validation = new CommonValidationHelper(new securityUtils());
        AccountManagementAuthorizationHelper hierarchyHelper = new AccountManagementAuthorizationHelper(
                accountRepository,
                accountRoleRepository,
                roleRepository,
                validation
        );
        accountRoleService = new AccountRoleService(accountRepository, accountRoleRepository, roleRepository, validation, hierarchyHelper, org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
        UUID dioceseId = UUID.randomUUID();
        Account target = dioceseAccount("phucuongadmin", dioceseId);
        Role targetAssignmentRole = role("ADMIN_PARISH", true, 300, "SYSTEM");
        AccountRole assignment = accountRole(target, targetAssignmentRole);
        assignment.setId("ar-1");

        when(accountRoleRepository.findById("ar-1")).thenReturn(Optional.of(assignment));
        when(accountRoleRepository.findActiveByUsername("phucuongadmin")).thenReturn(List.of(accountRole(target, role("ADMIN_DIOCESE", true, 100, "SYSTEM"))));
        when(accountRoleRepository.findActiveByUsername("datbung107")).thenReturn(List.of(accountRole(dioceseAccount("datbung107", dioceseId), role("ADMIN_DEANERY", true, 200, "SYSTEM"))));

        StatusToggleRequest request = new StatusToggleRequest();
        request.setConfirmationText("Xác nhận");

        assertThrows(RuntimeException.class, () -> accountRoleService.toggleStatus(
                "ar-1",
                request,
                authentication("datbung107", List.of("ADMIN_DEANERY"), List.of("system.account_role.assign.diocese"))
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

    private AccountRole accountRole(Account account, Role role) {
        AccountRole accountRole = new AccountRole();
        accountRole.setAccount(account);
        accountRole.setRole(role);
        accountRole.setStatus(true);
        return accountRole;
    }

    private UsernamePasswordAuthenticationToken authentication(String username, List<String> roles, List<String> permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, null, roles, permissions);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
