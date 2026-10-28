package phucuong.ht.modules.parish.security;

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
import phucuong.ht.modules.parish.repository.ParishRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParishAccessEvaluatorTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ParishRepository parishRepository;

    private ParishAccessEvaluator parishAccessEvaluator;

    @BeforeEach
    void setUp() {
        parishAccessEvaluator = new ParishAccessEvaluator(accountRepository, parishRepository, new securityUtils());
    }

    @Test
    void canReadShouldAllowSystemWidePermissionWithoutScopeLookup() {
        UUID parishId = UUID.randomUUID();
        Authentication authentication = authentication(
                "superadmin",
                List.of("SUPER_ADMIN"),
                List.of(ParishAccessEvaluator.READ_ALL)
        );

        assertTrue(parishAccessEvaluator.canRead(authentication, parishId));
        verifyNoInteractions(accountRepository, parishRepository);
    }

    @Test
    void canCreateShouldAllowDioceseScopedPermissionAndRejectSuperAdmin() {
        Authentication dioceseAdmin = authentication(
                "admin-diocese",
                List.of("ADMIN_DIOCESE"),
                List.of(ParishAccessEvaluator.CREATE_DIOCESE)
        );
        Authentication superAdmin = authentication(
                "superadmin",
                List.of("SUPER_ADMIN"),
                List.of(ParishAccessEvaluator.CREATE_DIOCESE)
        );

        assertTrue(parishAccessEvaluator.canCreate(dioceseAdmin));
        assertFalse(parishAccessEvaluator.canCreate(superAdmin));
    }

    @Test
    void canChangeParentShouldAllowCreatorsInScopeOnly() {
        UUID subUnitId = UUID.randomUUID();
        UUID dioceseId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        Authentication dioceseAdmin = authentication("admin-dio", List.of("ADMIN_DIOCESE"), List.of(ParishAccessEvaluator.CREATE_DIOCESE));
        Authentication deaneryAdmin = authentication("admin-dea", List.of("ADMIN_DEANERY"), List.of(ParishAccessEvaluator.CREATE_DEANERY));
        Authentication parishAdmin = authentication("admin-parish", List.of("ADMIN_PARISH"), List.of(ParishAccessEvaluator.READ_PARISH));
        when(accountRepository.findActiveDioceseIdByUsername("admin-dio")).thenReturn(Optional.of(dioceseId));
        when(parishRepository.findDioceseIdByParishId(subUnitId)).thenReturn(Optional.of(dioceseId));
        when(accountRepository.findActiveDeaneryIdByUsername("admin-dea")).thenReturn(Optional.of(deaneryId));
        when(parishRepository.findDeaneryIdByParishId(subUnitId)).thenReturn(Optional.of(UUID.randomUUID()));

        assertTrue(parishAccessEvaluator.canChangeParent(dioceseAdmin, subUnitId));
        assertFalse(parishAccessEvaluator.canChangeParent(deaneryAdmin, subUnitId));
        assertFalse(parishAccessEvaluator.canChangeParent(parishAdmin, subUnitId));
    }

    @Test
    void canReadShouldAllowDeaneryScopedPermissionForMatchingParish() {
        UUID parishId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        Authentication authentication = authentication(
                "admin-deanery",
                "LEADER1",
                List.of("ADMIN_DEANERY"),
                List.of(ParishAccessEvaluator.READ_DEANERY)
        );
        when(accountRepository.findActiveDeaneryIdByUsername("admin-deanery")).thenReturn(Optional.of(deaneryId));
        when(parishRepository.findDeaneryIdByParishId(parishId)).thenReturn(Optional.of(deaneryId));

        assertTrue(parishAccessEvaluator.canRead(authentication, parishId));
    }

    @Test
    void canUpdateShouldAllowParishScopedPermissionForMatchingParish() {
        UUID parishId = UUID.randomUUID();
        Authentication authentication = authentication(
                "admin-parish",
                "LEADER1",
                List.of("ADMIN_PARISH"),
                List.of(ParishAccessEvaluator.UPDATE_PARISH)
        );
        when(accountRepository.findActiveParishIdByUsername("admin-parish")).thenReturn(Optional.of(parishId));

        assertTrue(parishAccessEvaluator.canUpdate(authentication, parishId));
    }

    @Test
    void canUpdateShouldDenyParishScopedPermissionForDifferentParish() {
        Authentication authentication = authentication(
                "admin-parish",
                "LEADER1",
                List.of("ADMIN_PARISH"),
                List.of(ParishAccessEvaluator.UPDATE_PARISH)
        );
        when(accountRepository.findActiveParishIdByUsername("admin-parish")).thenReturn(Optional.of(UUID.randomUUID()));

        assertFalse(parishAccessEvaluator.canUpdate(authentication, UUID.randomUUID()));
    }

    @Test
    void canListShouldAllowParishScopedAuthority() {
        Authentication authentication = authentication(
                "admin-parish",
                "LEADER1",
                List.of("ADMIN_PARISH"),
                List.of(ParishAccessEvaluator.READ_PARISH)
        );

        assertTrue(parishAccessEvaluator.canList(authentication));
    }

    @Test
    void canToggleShouldAllowDeaneryScopedPermissionForMatchingParish() {
        UUID parishId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        Authentication authentication = authentication(
                "admin-deanery",
                "LEADER1",
                List.of("ADMIN_DEANERY"),
                List.of(ParishAccessEvaluator.TOGGLE_DEANERY)
        );
        when(accountRepository.findActiveDeaneryIdByUsername("admin-deanery")).thenReturn(Optional.of(deaneryId));
        when(parishRepository.findDeaneryIdByParishId(parishId)).thenReturn(Optional.of(deaneryId));

        assertTrue(parishAccessEvaluator.canToggle(authentication, parishId));
    }

    @Test
    void canToggleShouldDenyParishScopedPermissionForOwnParish() {
        UUID parishId = UUID.randomUUID();
        Authentication authentication = authentication(
                "admin-parish",
                "LEADER1",
                List.of("ADMIN_PARISH"),
                List.of(ParishAccessEvaluator.UPDATE_PARISH, ParishAccessEvaluator.TOGGLE_DEANERY)
        );

        assertFalse(parishAccessEvaluator.canToggle(authentication, parishId));
    }

    @Test
    void canToggleShouldDenySuperAdminEvenWithTogglePermission() {
        UUID parishId = UUID.randomUUID();
        Authentication superAdmin = authentication(
                "superadmin",
                List.of("SUPER_ADMIN"),
                List.of(ParishAccessEvaluator.TOGGLE_ALL)
        );

        assertFalse(parishAccessEvaluator.canToggle(superAdmin, parishId));
        verifyNoInteractions(accountRepository, parishRepository);
    }

    @Test
    void canToggleShouldAllowDioceseAdminForParishInOwnDioceseOnly() {
        UUID parishId = UUID.randomUUID();
        UUID dioceseId = UUID.randomUUID();
        UUID otherParishId = UUID.randomUUID();
        Authentication dioceseAdmin = authentication(
                "admin-dio",
                "LEADER1",
                List.of("ADMIN_DIOCESE"),
                List.of(ParishAccessEvaluator.TOGGLE_DIOCESE)
        );
        when(accountRepository.findActiveDioceseIdByUsername("admin-dio")).thenReturn(Optional.of(dioceseId));
        when(parishRepository.findDioceseIdByParishId(parishId)).thenReturn(Optional.of(dioceseId));
        when(parishRepository.findDioceseIdByParishId(otherParishId)).thenReturn(Optional.of(UUID.randomUUID()));

        assertTrue(parishAccessEvaluator.canToggle(dioceseAdmin, parishId));
        assertFalse(parishAccessEvaluator.canToggle(dioceseAdmin, otherParishId));
    }

    private Authentication authentication(String username, List<String> roles, List<String> permissions) {
        return authentication(username, null, roles, permissions);
    }

    private Authentication authentication(String username, String leaderId, List<String> roles, List<String> permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, leaderId, roles, permissions);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
