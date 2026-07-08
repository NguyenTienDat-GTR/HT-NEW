package phucuong.ht.modules.leader.security;

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
import phucuong.ht.modules.leader.repository.LeaderRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderAccessEvaluatorTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LeaderRepository leaderRepository;

    private LeaderAccessEvaluator leaderAccessEvaluator;

    @BeforeEach
    void setUp() {
        leaderAccessEvaluator = new LeaderAccessEvaluator(accountRepository, leaderRepository, new securityUtils());
    }

    @Test
    void canReadShouldAllowParishScopedPermissionForMatchingParish() {
        UUID parishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        Authentication authentication = authentication(
                "admin-parish",
                "LEADER1",
                List.of("ADMIN_PARISH"),
                List.of(LeaderAccessEvaluator.READ_PARISH)
        );
        when(accountRepository.findActiveParishIdByUsername("admin-parish")).thenReturn(Optional.of(parishId));
        when(leaderRepository.findParishIdByLeaderId(leaderId)).thenReturn(Optional.of(parishId));

        assertTrue(leaderAccessEvaluator.canRead(authentication, leaderId));
    }

    @Test
    void canUpdateShouldDenyForDifferentParish() {
        UUID leaderId = UUID.randomUUID();
        Authentication authentication = authentication(
                "admin-parish",
                "LEADER1",
                List.of("ADMIN_PARISH"),
                List.of(LeaderAccessEvaluator.UPDATE_PARISH)
        );
        when(accountRepository.findActiveParishIdByUsername("admin-parish")).thenReturn(Optional.of(UUID.randomUUID()));
        when(leaderRepository.findParishIdByLeaderId(leaderId)).thenReturn(Optional.of(UUID.randomUUID()));

        assertFalse(leaderAccessEvaluator.canUpdate(authentication, leaderId));
    }

    @Test
    void canListShouldAllowParishScopedAuthority() {
        Authentication authentication = authentication(
                "admin-parish",
                "LEADER1",
                List.of("ADMIN_PARISH"),
                List.of(LeaderAccessEvaluator.READ_PARISH)
        );

        assertTrue(leaderAccessEvaluator.canList(authentication));
    }

    @Test
    void canToggleShouldAllowMatchingParish() {
        UUID parishId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        Authentication authentication = authentication(
                "admin-parish",
                "LEADER1",
                List.of("ADMIN_PARISH"),
                List.of(LeaderAccessEvaluator.UPDATE_PARISH)
        );
        when(accountRepository.findActiveParishIdByUsername("admin-parish")).thenReturn(Optional.of(parishId));
        when(leaderRepository.findParishIdByLeaderId(leaderId)).thenReturn(Optional.of(parishId));

        assertTrue(leaderAccessEvaluator.canToggle(authentication, leaderId));
    }

    private Authentication authentication(String username, String leaderId, List<String> roles, List<String> permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, leaderId, roles, permissions);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
