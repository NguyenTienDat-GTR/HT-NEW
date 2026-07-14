package phucuong.ht.modules.diocese.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DioceseAccessEvaluatorTest {

    @Mock
    private AccountRepository accountRepository;

    private DioceseAccessEvaluator dioceseAccessEvaluator;

    @BeforeEach
    void setUp() {
        dioceseAccessEvaluator = new DioceseAccessEvaluator(accountRepository, new securityUtils());
    }

    @Test
    void canReadShouldAllowSystemWidePermissionWithoutScopeLookup() {
        UUID dioceseId = UUID.randomUUID();
        Authentication authentication = authentication(
                "superadmin",
                List.of("SUPER_ADMIN"),
                List.of(DioceseAccessEvaluator.READ_ALL)
        );

        assertTrue(dioceseAccessEvaluator.canRead(authentication, dioceseId));
        verifyNoInteractions(accountRepository);
    }

    @Test
    void canReadShouldAllowScopedPermissionForMatchingDiocese() {
        UUID dioceseId = UUID.randomUUID();
        Authentication authentication = authentication(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of(DioceseAccessEvaluator.READ_DIOCESE)
        );
        when(accountRepository.findActiveDioceseIdByUsername("admin-dio")).thenReturn(Optional.of(dioceseId));

        assertTrue(dioceseAccessEvaluator.canRead(authentication, dioceseId));
    }

    @Test
    void canReadShouldDenyScopedPermissionForDifferentDiocese() {
        UUID requestedId = UUID.randomUUID();
        UUID activeId = UUID.randomUUID();
        Authentication authentication = authentication(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of(DioceseAccessEvaluator.READ_DIOCESE)
        );
        when(accountRepository.findActiveDioceseIdByUsername("admin-dio")).thenReturn(Optional.of(activeId));

        assertFalse(dioceseAccessEvaluator.canRead(authentication, requestedId));
    }

    @Test
    void canUpdateShouldDenyWhenAccountHasNoResolvableDiocese() {
        UUID dioceseId = UUID.randomUUID();
        Authentication authentication = authentication(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of(DioceseAccessEvaluator.UPDATE_DIOCESE)
        );
        when(accountRepository.findActiveDioceseIdByUsername("admin-dio")).thenReturn(Optional.empty());

        assertFalse(dioceseAccessEvaluator.canUpdate(authentication, dioceseId));
    }

    @Test
    void canListShouldAllowDioceseScopedAuthority() {
        Authentication authentication = authentication(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of(DioceseAccessEvaluator.READ_DIOCESE)
        );

        assertTrue(dioceseAccessEvaluator.canList(authentication));
    }

    @Test
    void canToggleShouldAllowOnlySuperAdmin() {
        UUID dioceseId = UUID.randomUUID();
        Authentication superAdmin = authentication(
                "superadmin",
                List.of("SUPER_ADMIN"),
                List.of(DioceseAccessEvaluator.UPDATE_ALL)
        );
        Authentication dioceseAdmin = authentication(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of(DioceseAccessEvaluator.UPDATE_DIOCESE)
        );

        assertTrue(dioceseAccessEvaluator.canToggle(superAdmin, dioceseId));
        assertFalse(dioceseAccessEvaluator.canToggle(dioceseAdmin, dioceseId));
    }

    private Authentication authentication(String username, List<String> roles, List<String> permissions) {
        return authentication(username, null, roles, permissions);
    }

    private Authentication authentication(String username, String leaderId, List<String> roles, List<String> permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, leaderId, roles, permissions);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
