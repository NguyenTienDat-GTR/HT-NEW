package phucuong.ht.modules.analytics.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.analytics.model.AnalyticsScopeType;
import phucuong.ht.modules.auth.repository.AccountRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsScopeResolverTest {
    @Mock private AccountRepository accountRepository;
    private AnalyticsScopeResolver resolver;
    @org.junit.jupiter.api.BeforeEach void setUp() { resolver = new AnalyticsScopeResolver(new securityUtils(), accountRepository); }
    @Test void resolvesDioceseScopeFromCurrentAccount() { UUID id=UUID.randomUUID(); when(accountRepository.findActiveDioceseIdByUsername("u")).thenReturn(Optional.of(id)); var result=resolver.resolve(auth(AnalyticsAccessEvaluator.READ_DIOCESE),false); assertEquals(AnalyticsScopeType.DIOCESE,result.type()); assertEquals(id,result.dioceseId()); }
    @Test void resolvesDeaneryAndParishWithoutScopeExpansion() { UUID deanery=UUID.randomUUID(); when(accountRepository.findActiveDeaneryIdByUsername("u")).thenReturn(Optional.of(deanery)); assertEquals(AnalyticsScopeType.DEANERY,resolver.resolve(auth(AnalyticsAccessEvaluator.READ_DEANERY),false).type()); UUID parish=UUID.randomUUID(); when(accountRepository.findActiveParishIdByUsername("u")).thenReturn(Optional.of(parish)); assertEquals(parish,resolver.resolve(auth(AnalyticsAccessEvaluator.READ_PARISH),false).parishId()); }
    @Test void allPermissionResolvesOnlySystemOverview() { assertEquals(AnalyticsScopeType.ALL,resolver.resolve(auth(AnalyticsAccessEvaluator.READ_ALL),true).type()); }
    private UsernamePasswordAuthenticationToken auth(String authority) { AuthUserDetails principal=AuthUserDetails.tokenPrincipal("u", "leader", List.of(), List.of(authority)); return new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()); }
}
