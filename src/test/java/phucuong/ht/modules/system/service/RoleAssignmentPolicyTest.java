package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.system.model.entity.Role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoleAssignmentPolicyTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AccountRoleRepository accountRoles = mock(AccountRoleRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final AccountManagementAuthorizationHelper authorization = mock(AccountManagementAuthorizationHelper.class);
    private final RoleAssignmentPolicy policy =
            new RoleAssignmentPolicy(accounts, accountRoles, roles, new CommonValidationHelper(new securityUtils()), authorization);

    private static UsernamePasswordAuthenticationToken auth(String username, List<String> roleCodes, List<String> permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, null, roleCodes, permissions);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static Role role(String code, boolean system, int order) {
        Role role = new Role();
        role.setRoleCode(code);
        role.setIsSystem(system);
        role.setDisplayOrder(order);
        role.setStatus(true);
        return role;
    }

    private static Account account(String username) {
        Account account = new Account();
        account.setUsername(username);
        return account;
    }

    @Test
    void anAdminCannotAssignRolesToTheirOwnAccount() {
        var admin = auth("gp", List.of("ADMIN_DIOCESE"), List.of("system.account_role.assign.diocese"));

        assertThrows(ApiException.class, () -> policy.enforce(admin, account("gp"), role("CUSTOM", false, 50)));
    }

    @Test
    void dioceseAdminAssignsWithinTheirOwnDioceseOnly() {
        UUID mine = UUID.randomUUID();
        var admin = auth("gp", List.of("ADMIN_DIOCESE"), List.of("system.account_role.assign.diocese"));
        when(authorization.currentActorHighestRoleOrder(admin)).thenReturn(10);
        when(accounts.findActiveDioceseIdByUsername("gp")).thenReturn(Optional.of(mine));
        when(accounts.findActiveDioceseIdByUsername("same")).thenReturn(Optional.of(mine));
        when(accounts.findActiveDioceseIdByUsername("other")).thenReturn(Optional.of(UUID.randomUUID()));
        when(accounts.findActiveDioceseIdByUsername("none")).thenReturn(Optional.empty());
        Role custom = role("CUSTOM", false, 50);

        policy.enforce(admin, account("same"), custom);
        assertThrows(ApiException.class, () -> policy.enforce(admin, account("other"), custom));
        assertThrows(ApiException.class, () -> policy.enforce(admin, account("none"), custom));
    }

    @Test
    void scopedAdminsCannotAssignRolesAboveTheirHierarchyOrSystemRolesOutsideTheirTier() {
        var deanery = auth("gh", List.of("ADMIN_DEANERY"), List.of("system.account_role.assign.deanery"));
        UUID same = UUID.randomUUID();
        when(authorization.currentActorHighestRoleOrder(deanery)).thenReturn(30);
        when(accounts.findActiveDeaneryIdByUsername("gh")).thenReturn(Optional.of(same));
        when(accounts.findActiveDeaneryIdByUsername("t")).thenReturn(Optional.of(same));

        assertThrows(ApiException.class, () -> policy.enforce(deanery, account("t"), role("CUSTOM", false, 20)));
        assertThrows(ApiException.class, () -> policy.enforce(deanery, account("t"), role("ADMIN_DIOCESE", true, 40)));
        policy.enforce(deanery, account("t"), role("ADMIN_PARISH", true, 40));
    }

    @Test
    void withoutAnyAssignPermissionNothingCanBeAssigned() {
        var nobody = auth("x", List.of("ADMIN_PARISH"), List.of());

        assertFalse(policy.canAssign(nobody, role("CUSTOM", false, 50)));
        assertThrows(ApiException.class, () -> policy.enforce(nobody, account("t"), role("CUSTOM", false, 50)));
    }

    @Test
    void superAdminMayOnlyAssignSuperAdminDioceseAdminAndTheirOwnCustomRoles() {
        var root = auth("root", List.of("SUPER_ADMIN"), List.of());
        Role ownCustom = role("MINE", false, 50);
        ownCustom.setCreatedBy("super-admin");

        assertTrue(policy.canAssign(root, role("ADMIN_DIOCESE", true, 10)));
        assertTrue(policy.canAssign(root, ownCustom));
        assertFalse(policy.canAssign(root, role("ADMIN_PARISH", true, 40)));
        assertFalse(policy.canAssign(root, role("CUSTOM", false, 50)));
    }
}
