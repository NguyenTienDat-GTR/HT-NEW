package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.RoleRepository;
import phucuong.ht.modules.system.model.entity.Role;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

// Thu hồi theo thứ tự role của người cấp; phân quyền tài khoản chỉ có createdBy làm người cấp.
class RevocableOriginTest {

    private final AccountRepository accountRepository = Mockito.mock(AccountRepository.class);
    private final RoleRepository roleRepository = Mockito.mock(RoleRepository.class);

    private SystemAdminAuthorizationHelper helper() {
        return new SystemAdminAuthorizationHelper(
                new securityUtils(),
                Mockito.mock(AccountScopeValidationHelper.class),
                accountRepository,
                roleRepository,
                new CommonValidationHelper(new securityUtils())
        );
    }

    private UsernamePasswordAuthenticationToken actor(String roleCode, int order) {
        Role role = new Role();
        role.setRoleCode(roleCode);
        role.setDisplayOrder(order);
        when(roleRepository.findByRoleCodeAndStatusTrue(roleCode)).thenReturn(Optional.of(role));
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("actor", null, List.of(roleCode), List.of());
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void sameOrHigherRoleThanGranterMayRevoke() {
        when(accountRepository.findHighestActiveRoleOrder("granter")).thenReturn(100);
        assertDoesNotThrow(() -> helper().requireRevocableOrigin(actor("ADMIN_DIOCESE", 100), null, "granter"));
        assertDoesNotThrow(() -> helper().requireRevocableOrigin(actor("ADMIN_DIOCESE", 50), null, "granter"));
    }

    @Test
    void lowerRoleThanGranterMayNotRevoke() {
        when(accountRepository.findHighestActiveRoleOrder("granter")).thenReturn(50);
        assertThrows(RuntimeException.class, () -> helper().requireRevocableOrigin(actor("ADMIN_DEANERY", 100), null, "granter"));
    }

    @Test
    void systemOrRolelessGranterIsSuperAdminOnly() {
        assertThrows(RuntimeException.class, () -> helper().requireRevocableOrigin(actor("ADMIN_DIOCESE", 100), null, "system"));
        when(accountRepository.findHighestActiveRoleOrder("gone")).thenReturn(null);
        assertThrows(RuntimeException.class, () -> helper().requireRevocableOrigin(actor("ADMIN_DIOCESE", 100), null, "gone"));
    }
}
