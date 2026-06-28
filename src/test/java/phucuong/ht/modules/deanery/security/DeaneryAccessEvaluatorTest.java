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
        Authentication authentication = authentication(
                "superadmin",
                List.of("SUPER_ADMIN"),
                List.of(DeaneryAccessEvaluator.READ_ALL)
        );

        assertTrue(deaneryAccessEvaluator.canRead(authentication, "DEA1"));
        verifyNoInteractions(accountRepository, deaneryRepository);
    }

    @Test
    void canReadShouldAllowDioceseScopedPermissionForMatchingDeanery() {
        Authentication authentication = authentication(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of(DeaneryAccessEvaluator.READ_DIOCESE)
        );
        when(accountRepository.findActiveDioceseCodeByUsername("admin-dio")).thenReturn(Optional.of("DIO1"));
        when(deaneryRepository.findDioceseCodeByDeaneryCode("DEA1")).thenReturn(Optional.of("DIO1"));

        assertTrue(deaneryAccessEvaluator.canRead(authentication, "DEA1"));
    }

    @Test
    void canReadShouldDenyDioceseScopedPermissionForDifferentDeanery() {
        Authentication authentication = authentication(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of(DeaneryAccessEvaluator.READ_DIOCESE)
        );
        when(accountRepository.findActiveDioceseCodeByUsername("admin-dio")).thenReturn(Optional.of("DIO1"));
        when(deaneryRepository.findDioceseCodeByDeaneryCode("DEA1")).thenReturn(Optional.of("DIO2"));

        assertFalse(deaneryAccessEvaluator.canRead(authentication, "DEA1"));
    }

    @Test
    void canUpdateShouldAllowDeaneryScopedPermissionForMatchingDeanery() {
        Authentication authentication = authentication(
                "admin-deanery",
                "LEADER1",
                List.of("ADMIN_DEANERY"),
                List.of(DeaneryAccessEvaluator.UPDATE_DEANERY)
        );
        when(accountRepository.findActiveDeaneryCodeByUsername("admin-deanery")).thenReturn(Optional.of("DEA1"));

        assertTrue(deaneryAccessEvaluator.canUpdate(authentication, "DEA1"));
    }

    @Test
    void canUpdateShouldDenyDeaneryScopedPermissionForDifferentDeanery() {
        Authentication authentication = authentication(
                "admin-deanery",
                "LEADER1",
                List.of("ADMIN_DEANERY"),
                List.of(DeaneryAccessEvaluator.UPDATE_DEANERY)
        );
        when(accountRepository.findActiveDeaneryCodeByUsername("admin-deanery")).thenReturn(Optional.of("DEA2"));

        assertFalse(deaneryAccessEvaluator.canUpdate(authentication, "DEA1"));
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

    @Test
    void canCreateShouldAllowDioceseScopedAuthority() {
        Authentication authentication = authentication(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of(DeaneryAccessEvaluator.CREATE_DIOCESE)
        );

        assertTrue(deaneryAccessEvaluator.canCreate(authentication));
    }

    @Test
    void canDeleteShouldAllowDioceseScopedAuthority() {
        Authentication authentication = authentication(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of(DeaneryAccessEvaluator.DELETE_DIOCESE)
        );

        assertTrue(deaneryAccessEvaluator.canDelete(authentication));
    }

    @Test
    void canDeleteShouldDenyDeaneryScopedAuthority() {
        Authentication authentication = authentication(
                "admin-deanery",
                "LEADER1",
                List.of("ADMIN_DEANERY"),
                List.of(DeaneryAccessEvaluator.UPDATE_DEANERY)
        );

        assertFalse(deaneryAccessEvaluator.canDelete(authentication));
    }

    private Authentication authentication(String username, List<String> roles, List<String> permissions) {
        return authentication(username, null, roles, permissions);
    }

    private Authentication authentication(String username, String leaderId, List<String> roles, List<String> permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, leaderId, roles, permissions);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
