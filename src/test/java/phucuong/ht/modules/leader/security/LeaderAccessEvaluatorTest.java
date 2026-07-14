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
    void canReadShouldAllowDioceseScopedPermissionForMatchingLeader() {
        UUID dioceseId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        Authentication authentication = authentication(
                "admin-diocese",
                null,
                List.of("ADMIN_DIOCESE"),
                List.of(LeaderAccessEvaluator.READ_DIOCESE)
        );
        when(accountRepository.findActiveDioceseIdByUsername("admin-diocese")).thenReturn(Optional.of(dioceseId));
        when(leaderRepository.findDioceseIdByLeaderId(leaderId)).thenReturn(Optional.of(dioceseId));

        assertTrue(leaderAccessEvaluator.canRead(authentication, leaderId));
    }

    @Test
    void canReadShouldAllowDeaneryScopedPermissionForMatchingLeader() {
        UUID deaneryId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        Authentication authentication = authentication(
                "admin-deanery",
                "LEADER1",
                List.of("ADMIN_DEANERY"),
                List.of(LeaderAccessEvaluator.READ_DEANERY)
        );
        when(accountRepository.findActiveDeaneryIdByUsername("admin-deanery")).thenReturn(Optional.of(deaneryId));
        when(leaderRepository.findDeaneryIdByLeaderId(leaderId)).thenReturn(Optional.of(deaneryId));

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
    void canListShouldAllowDioceseScopedAuthority() {
        Authentication authentication = authentication(
                "admin-diocese",
                null,
                List.of("ADMIN_DIOCESE"),
                List.of(LeaderAccessEvaluator.READ_DIOCESE)
        );

        assertTrue(leaderAccessEvaluator.canList(authentication));
    }

    @Test
    void canCreateShouldAllowDioceseAdminWithDiocesePermission() {
        Authentication authentication = authentication(
                "admin-diocese", null, List.of("ADMIN_DIOCESE"), List.of(LeaderAccessEvaluator.CREATE_DIOCESE));
        assertTrue(leaderAccessEvaluator.canCreate(authentication));
    }

    @Test
    void canCreateShouldDenySuperAdminEvenWithPermission() {
        Authentication authentication = authentication(
                "super-admin", null, List.of("SUPER_ADMIN"), List.of(LeaderAccessEvaluator.CREATE_DIOCESE));
        assertFalse(leaderAccessEvaluator.canCreate(authentication));
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
