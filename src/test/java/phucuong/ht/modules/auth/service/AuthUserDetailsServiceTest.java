package phucuong.ht.modules.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import phucuong.ht.common.security.UnitOperationalStatusService;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.auth.repository.AuthPermissionQueryRepository;
import phucuong.ht.modules.system.model.entity.Account;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthUserDetailsServiceTest {

    private static final String READ_LEADER = "organization.leader.read.deanery";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountRoleRepository accountRoleRepository;

    @Mock
    private AuthPermissionQueryRepository authPermissionQueryRepository;

    @Mock
    private UnitOperationalStatusService unitOperationalStatusService;

    @Test
    void roleDenyShouldNotSuppressAllowFromAnotherActiveRole() {
        givenActiveAccount(
                List.of("ADMIN_DEANERY", "ADMIN_PARISH"),
                List.of(
                        rolePermission(READ_LEADER, "DENY", "ADMIN_DEANERY"),
                        rolePermission(READ_LEADER, "ALLOW", "ADMIN_PARISH")
                )
        );

        AuthUserDetails principal = service().loadUserByUsername("target");

        assertEquals(List.of(READ_LEADER), principal.getPermissions());
    }

    @Test
    void directDenyShouldStillBlockAfterParentPermissionIsReenabled() {
        givenBaseAccount();
        when(accountRoleRepository.findActiveRoleCodesByUsername("target"))
                .thenReturn(List.of("ADMIN_PARISH"), List.of("ADMIN_PARISH"));
        when(authPermissionQueryRepository.findEffectivePermissionsByUsername("target"))
                .thenReturn(
                        List.of(),
                        List.of(
                                rolePermission(READ_LEADER, "ALLOW", "ADMIN_PARISH"),
                                accountOverride(READ_LEADER, "DENY")
                        )
                );

        AuthUserDetails whileParentPermissionDisabled = service().loadUserByUsername("target");
        AuthUserDetails afterParentPermissionReenabled = service().loadUserByUsername("target");

        assertEquals(List.of(), whileParentPermissionDisabled.getPermissions());
        assertEquals(List.of(), afterParentPermissionReenabled.getPermissions());
    }

    @Test
    void directAllowShouldRecoverAfterParentPermissionIsReenabledAndOverrideRoleDeny() {
        givenBaseAccount();
        when(accountRoleRepository.findActiveRoleCodesByUsername("target"))
                .thenReturn(List.of("ADMIN_DEANERY"), List.of("ADMIN_DEANERY"));
        when(authPermissionQueryRepository.findEffectivePermissionsByUsername("target"))
                .thenReturn(
                        List.of(),
                        List.of(
                                rolePermission(READ_LEADER, "DENY", "ADMIN_DEANERY"),
                                accountOverride(READ_LEADER, "ALLOW")
                        )
                );

        AuthUserDetails whileParentPermissionDisabled = service().loadUserByUsername("target");
        AuthUserDetails afterParentPermissionReenabled = service().loadUserByUsername("target");

        assertEquals(List.of(), whileParentPermissionDisabled.getPermissions());
        assertEquals(List.of(READ_LEADER), afterParentPermissionReenabled.getPermissions());
    }

    @Test
    void activeAccountRoleShouldRecoverAfterParentRoleIsReenabledWhileDisabledMappingStaysAbsent() {
        givenBaseAccount();
        when(accountRoleRepository.findActiveRoleCodesByUsername("target"))
                .thenReturn(List.of(), List.of("ADMIN_PARISH"));
        when(authPermissionQueryRepository.findEffectivePermissionsByUsername("target"))
                .thenReturn(
                        List.of(),
                        List.of(rolePermission(READ_LEADER, "ALLOW", "ADMIN_PARISH"))
                );

        AuthUserDetails whileParentRoleDisabled = service().loadUserByUsername("target");
        AuthUserDetails afterParentRoleReenabled = service().loadUserByUsername("target");

        assertEquals(List.of(), whileParentRoleDisabled.getRoles());
        assertEquals(List.of(), whileParentRoleDisabled.getPermissions());
        assertEquals(List.of("ADMIN_PARISH"), afterParentRoleReenabled.getRoles());
        assertEquals(List.of(READ_LEADER), afterParentRoleReenabled.getPermissions());
    }

    @Test
    void lockedUnitShouldReturnEmptyRolesAndPermissions() {
        givenBaseAccount();
        when(unitOperationalStatusService.isAccountUnitLocked("target")).thenReturn(true);

        AuthUserDetails principal = service().loadUserByUsername("target");

        assertEquals(List.of(), principal.getRoles());
        assertEquals(List.of(), principal.getPermissions());
    }

    private void givenActiveAccount(
            List<String> roleCodes,
            List<AuthPermissionQueryRepository.EffectivePermissionRow> permissionRows
    ) {
        givenBaseAccount();
        when(accountRoleRepository.findActiveRoleCodesByUsername("target")).thenReturn(roleCodes);
        when(authPermissionQueryRepository.findEffectivePermissionsByUsername("target")).thenReturn(permissionRows);
    }

    private void givenBaseAccount() {
        Account account = new Account();
        account.setUsername("target");
        account.setPassword("{noop}secret");
        account.setStatus(true);
        account.setMustChangePassword(false);
        when(accountRepository.findActiveByUsername("target")).thenReturn(Optional.of(account));
        lenient().when(unitOperationalStatusService.isAccountUnitLocked("target")).thenReturn(false);
    }

    private AuthUserDetailsService service() {
        return new AuthUserDetailsService(accountRepository, accountRoleRepository, authPermissionQueryRepository, unitOperationalStatusService);
    }

    private AuthPermissionQueryRepository.EffectivePermissionRow rolePermission(String permissionCode, String effect, String roleCode) {
        return new AuthPermissionQueryRepository.EffectivePermissionRow(permissionCode, effect, "ROLE", roleCode);
    }

    private AuthPermissionQueryRepository.EffectivePermissionRow accountOverride(String permissionCode, String effect) {
        return new AuthPermissionQueryRepository.EffectivePermissionRow(permissionCode, effect, "ACCOUNT_OVERRIDE", null);
    }
}
