package phucuong.ht.modules.diocese.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
        Authentication authentication = authentication(
                "superadmin",
                List.of("SUPER_ADMIN"),
                List.of(DioceseAccessEvaluator.READ_ALL)
        );

        assertTrue(dioceseAccessEvaluator.canRead(authentication, "DIO1"));
        verifyNoInteractions(accountRepository);
    }

    @Test
    void canReadShouldAllowScopedPermissionForMatchingDiocese() {
        Authentication authentication = authentication(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of(DioceseAccessEvaluator.READ_DIOCESE)
        );
        when(accountRepository.findActiveDioceseCodeByUsername("admin-dio")).thenReturn(Optional.of("DIO1"));

        assertTrue(dioceseAccessEvaluator.canRead(authentication, "DIO1"));
    }

    @Test
    void canReadShouldDenyScopedPermissionForDifferentDiocese() {
        Authentication authentication = authentication(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of(DioceseAccessEvaluator.READ_DIOCESE)
        );
        when(accountRepository.findActiveDioceseCodeByUsername("admin-dio")).thenReturn(Optional.of("DIO2"));

        assertFalse(dioceseAccessEvaluator.canRead(authentication, "DIO1"));
    }

    @Test
    void canUpdateShouldDenyWhenAccountHasNoResolvableDiocese() {
        Authentication authentication = authentication(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of(DioceseAccessEvaluator.UPDATE_DIOCESE)
        );
        when(accountRepository.findActiveDioceseCodeByUsername("admin-dio")).thenReturn(Optional.empty());

        assertFalse(dioceseAccessEvaluator.canUpdate(authentication, "DIO1"));
    }

    @Test
    void canListShouldDenyDioceseScopedAuthority() {
        Authentication authentication = authentication(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of(DioceseAccessEvaluator.READ_DIOCESE)
        );

        assertFalse(dioceseAccessEvaluator.canList(authentication));
    }

    private Authentication authentication(String username, List<String> roles, List<String> permissions) {
        return authentication(username, null, roles, permissions);
    }

    private Authentication authentication(String username, String leaderId, List<String> roles, List<String> permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, leaderId, roles, permissions);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
