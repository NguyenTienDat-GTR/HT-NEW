package phucuong.ht.modules.certificate.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.certificate.config.security.CertificateConfigScopeResolver.ActorScope;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CertificateConfigScopeResolverTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final CertificateConfigScopeResolver resolver = new CertificateConfigScopeResolver(
            new securityUtils(), accountRepository, new CommonValidationHelper(new securityUtils()));
    private final UUID dioceseId = UUID.randomUUID();
    private final UUID deaneryId = UUID.randomUUID();
    private final String dioceseKey = CertificateConfigScopeResolver.keyOf(CertificateConfigScopeResolver.DIOCESE, dioceseId);
    private final String deaneryKey = CertificateConfigScopeResolver.keyOf(CertificateConfigScopeResolver.DEANERY, deaneryId);

    private ActorScope resolve(String... permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("multi", null, List.of("ADMIN_DEANERY"), List.of(permissions));
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(accountRepository.findActiveDioceseIdByUsername("multi")).thenReturn(Optional.of(dioceseId));
        when(accountRepository.findActiveDeaneryIdByUsername("multi")).thenReturn(Optional.of(deaneryId));
        return resolver.resolve(auth);
    }

    @Test
    void dioceseAndDeaneryWritersUseAndEditBothScopes() {
        ActorScope scope = resolve("training.certificate_config.update.diocese", "training.certificate_config.update.deanery");

        assertTrue(scope.canSee(dioceseKey));
        assertTrue(scope.canSee(deaneryKey));
        assertTrue(scope.canEdit(dioceseKey));
        assertTrue(scope.canEdit(deaneryKey));
    }

    @Test
    void readOnlyDeaneryPermissionDoesNotLetADioceseEditorEditDeaneryConfigs() {
        ActorScope scope = resolve("training.certificate_config.update.diocese", "training.certificate_config.read.deanery");

        assertTrue(scope.canSee(deaneryKey));
        assertTrue(scope.canEdit(dioceseKey));
        assertFalse(scope.canEdit(deaneryKey));
    }

    @Test
    void deaneryOnlyAccountEditsItsOwnDeaneryAndSeesTheInheritedDioceseConfigs() {
        ActorScope scope = resolve("training.certificate_config.update.deanery");

        assertEquals(deaneryKey, scope.key());
        assertTrue(scope.canEdit(deaneryKey));
        assertFalse(scope.canEdit(dioceseKey));
        assertTrue(scope.canSee(dioceseKey));
    }
}
