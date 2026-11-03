package phucuong.ht.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.security.AccountScopeResolver.Level;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountScopeResolverTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AccountScopeResolver resolver = new AccountScopeResolver(new securityUtils(), accountRepository);
    private final UUID dioceseId = UUID.randomUUID();
    private final UUID deaneryId = UUID.randomUUID();
    private final UUID parishId = UUID.randomUUID();

    private Authentication authentication(String... permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("multi-role", null, List.of("ADMIN_DEANERY", "ADMIN_PARISH"), List.of(permissions));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private void units() {
        when(accountRepository.findActiveDioceseIdByUsername("multi-role")).thenReturn(Optional.of(dioceseId));
        when(accountRepository.findActiveDeaneryIdByUsername("multi-role")).thenReturn(Optional.of(deaneryId));
        when(accountRepository.findActiveParishIdByUsername("multi-role")).thenReturn(Optional.of(parishId));
    }

    @Test
    void unionsEveryLevelTheAccountHasAPermissionFor() {
        units();

        var scopes = resolver.scopes(authentication("training.participation.read.deanery", "training.participation.read.parish"), "training.participation.read");

        assertEquals(List.of(new AccountScopeResolver.Scope(Level.DEANERY, deaneryId), new AccountScopeResolver.Scope(Level.PARISH, parishId)), scopes.scopes());
        assertNull(scopes.unitId(Level.DIOCESE));
        assertEquals(deaneryId, scopes.unitId(Level.DEANERY));
    }

    @Test
    void skipsLevelsWithoutAnEffectiveUnit() {
        when(accountRepository.findActiveDeaneryIdByUsername("multi-role")).thenReturn(Optional.of(deaneryId));
        when(accountRepository.findActiveParishIdByUsername("multi-role")).thenReturn(Optional.empty());

        var scopes = resolver.scopes(authentication("x.read.deanery", "x.read.parish"), "x.read");

        assertEquals(1, scopes.scopes().size());
        assertTrue(scopes.has(Level.DEANERY));
        assertFalse(scopes.has(Level.PARISH));
    }

    @Test
    void noMatchingPermissionGivesAnEmptyScopeSet() {
        units();

        assertTrue(resolver.scopes(authentication("other.read.deanery"), "x.read").isEmpty());
    }

    @Test
    void acceptsAnyOfSeveralPermissionsPerLevel() {
        units();

        var scopes = resolver.scopesAny(authentication("y.approve.diocese"),
                List.of("y.read.diocese", "y.approve.diocese"), List.of("y.read.deanery"), List.of("y.read.parish"));

        assertEquals(List.of(new AccountScopeResolver.Scope(Level.DIOCESE, dioceseId)), scopes.scopes());
    }

    @Test
    void groupsBecomeOneConditionPerScope() {
        units();

        var groups = resolver.scopes(authentication("x.read.diocese", "x.read.deanery"), "x.read")
                .groups(Map.of(Level.DIOCESE, "diocese.id", Level.DEANERY, "deanery.id"));

        assertEquals(List.of(Map.of("diocese.id", dioceseId), Map.of("deanery.id", deaneryId)), groups);
    }
}
