package phucuong.ht.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.auth.repository.AccountRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheScopeKeyHelperTest {

    @Mock
    private AccountRepository accountRepository;

    @Test
    void listKeyShouldChangeWhenResolvedScopeChanges() {
        CacheScopeKeyHelper helper = new CacheScopeKeyHelper(new securityUtils(), accountRepository);
        UUID dioceseOne = UUID.randomUUID();
        UUID dioceseTwo = UUID.randomUUID();

        when(accountRepository.findActiveDioceseIdByUsername("dio-1")).thenReturn(java.util.Optional.of(dioceseOne));
        when(accountRepository.findActiveDioceseIdByUsername("dio-2")).thenReturn(java.util.Optional.of(dioceseTwo));
        when(accountRepository.findActiveDeaneryIdByUsername("dio-1")).thenReturn(java.util.Optional.empty());
        when(accountRepository.findActiveDeaneryIdByUsername("dio-2")).thenReturn(java.util.Optional.empty());
        when(accountRepository.findActiveParishIdByUsername("dio-1")).thenReturn(java.util.Optional.empty());
        when(accountRepository.findActiveParishIdByUsername("dio-2")).thenReturn(java.util.Optional.empty());

        BaseSearchRequest request = new BaseSearchRequest();
        request.setPage(0);
        request.setSize(20);
        request.setSearch("martin");

        String keyOne = helper.listKey(authentication("dio-1", List.of("organization.leader.read.parish")), request);
        String keyTwo = helper.listKey(authentication("dio-2", List.of("organization.leader.read.parish")), request);

        assertNotEquals(keyOne, keyTwo);
    }

    @Test
    void listKeyShouldNormalizeAuthorityAndFilterOrdering() {
        CacheScopeKeyHelper helper = new CacheScopeKeyHelper(new securityUtils(), accountRepository);

        when(accountRepository.findActiveDioceseIdByUsername("admin")).thenReturn(java.util.Optional.empty());
        when(accountRepository.findActiveDeaneryIdByUsername("admin")).thenReturn(java.util.Optional.empty());
        when(accountRepository.findActiveParishIdByUsername("admin")).thenReturn(java.util.Optional.empty());

        BaseSearchRequest left = new BaseSearchRequest();
        left.setSearchFields(List.of("leader.firstName", "leader.lastName"));
        LinkedHashMap<String, Object> leftFilters = new LinkedHashMap<>();
        leftFilters.put("position.positionCode", "POS-1");
        leftFilters.put("diocese.id", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        left.setFilters(leftFilters);

        BaseSearchRequest right = new BaseSearchRequest();
        right.setSearchFields(List.of("leader.lastName", "leader.firstName"));
        LinkedHashMap<String, Object> rightFilters = new LinkedHashMap<>();
        rightFilters.put("diocese.id", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        rightFilters.put("position.positionCode", "POS-1");
        right.setFilters(rightFilters);

        String leftKey = helper.listKey(authentication("admin", List.of("b", "a")), left);
        String rightKey = helper.listKey(authentication("admin", List.of("a", "b")), right);

        assertEquals(leftKey, rightKey);
    }

    private UsernamePasswordAuthenticationToken authentication(String username, List<String> permissions) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, "leader-id", List.of("ROLE"), permissions);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
