package phucuong.ht.modules.deanery.security;

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
import phucuong.ht.modules.deanery.repository.DeaneryRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeaneryAccessEvaluatorTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DeaneryRepository deaneryRepository;

    private DeaneryAccessEvaluator deaneryAccessEvaluator;

    @BeforeEach
    void setUp() {
        deaneryAccessEvaluator = new DeaneryAccessEvaluator(accountRepository, deaneryRepository, new securityUtils());
    }

    @Test
    void canReadShouldAllowSystemWidePermissionWithoutScopeLookup() {
        UUID deaneryId = UUID.randomUUID();
        Authentication authentication = authentication(
                "superadmin",
                List.of("SUPER_ADMIN"),
                List.of(DeaneryAccessEvaluator.READ_ALL)
        );

        assertTrue(deaneryAccessEvaluator.canRead(authentication, deaneryId));
        verifyNoInteractions(accountRepository, deaneryRepository);
    }

    @Test
    void canReadShouldAllowDioceseScopedPermissionForMatchingDeanery() {
        UUID deaneryId = UUID.randomUUID();
        UUID dioceseId = UUID.randomUUID();
        Authentication authentication = authentication(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of(DeaneryAccessEvaluator.READ_DIOCESE)
        );
        when(accountRepository.findActiveDioceseIdByUsername("admin-dio")).thenReturn(Optional.of(dioceseId));
        when(deaneryRepository.findDioceseIdByDeaneryId(deaneryId)).thenReturn(Optional.of(dioceseId));

        assertTrue(deaneryAccessEvaluator.canRead(authentication, deaneryId));
    }

    @Test
    void canReadShouldDenyDioceseScopedPermissionForDifferentDeanery() {
        UUID deaneryId = UUID.randomUUID();
        Authentication authentication = authentication(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of(DeaneryAccessEvaluator.READ_DIOCESE)
        );
        when(accountRepository.findActiveDioceseIdByUsername("admin-dio")).thenReturn(Optional.of(UUID.randomUUID()));
        when(deaneryRepository.findDioceseIdByDeaneryId(deaneryId)).thenReturn(Optional.of(UUID.randomUUID()));

        assertFalse(deaneryAccessEvaluator.canRead(authentication, deaneryId));
    }

    @Test
    void canUpdateShouldAllowDeaneryScopedPermissionForMatchingDeanery() {
        UUID deaneryId = UUID.randomUUID();
        Authentication authentication = authentication(
                "admin-deanery",
                "LEADER1",
                List.of("ADMIN_DEANERY"),
                List.of(DeaneryAccessEvaluator.UPDATE_DEANERY)
        );
        when(accountRepository.findActiveDeaneryIdByUsername("admin-deanery")).thenReturn(Optional.of(deaneryId));

        assertTrue(deaneryAccessEvaluator.canUpdate(authentication, deaneryId));
    }

    @Test
    void canUpdateShouldDenyDeaneryScopedPermissionForDifferentDeanery() {
        Authentication authentication = authentication(
                "admin-deanery",
                "LEADER1",
                List.of("ADMIN_DEANERY"),
                List.of(DeaneryAccessEvaluator.UPDATE_DEANERY)
        );
        when(accountRepository.findActiveDeaneryIdByUsername("admin-deanery")).thenReturn(Optional.of(UUID.randomUUID()));

        assertFalse(deaneryAccessEvaluator.canUpdate(authentication, UUID.randomUUID()));
    }

    @Test
    void canListShouldAllowDeaneryScopedAuthority() {
        Authentication authentication = authentication(
                "admin-deanery",
                "LEADER1",
                List.of("ADMIN_DEANERY"),
                List.of(DeaneryAccessEvaluator.READ_DEANERY)
        );

        assertTrue(deaneryAccessEvaluator.canList(authentication));
    }

    private Authentication authentication(String username, List<String> roles, List<String> permissions) {
        return authentication(username, null, roles, permissions);
    }

    private Authentication authentication(String username, String leaderId, List<String> roles, List<String> permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, leaderId, roles, permissions);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
