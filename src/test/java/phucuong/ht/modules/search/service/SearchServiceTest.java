package phucuong.ht.modules.search.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.search.model.QuickSearchGroup;
import phucuong.ht.modules.search.repository.SearchRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class SearchServiceTest {

    @Mock
    private SearchRepository searchRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private securityUtils securityUtils;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private SearchService searchService;

    @Test
    void shortQueryReturnsNothingWithoutQueryingDatabase() {
        assertTrue(searchService.quick(" a ", 5, authentication).isEmpty());
        verify(searchRepository, never()).rows(anyString(), anyMap(), anyInt());
    }

    @Test
    void accountWithoutReadPermissionGetsNoGroups() {
        when(securityUtils.currentUsername(authentication)).thenReturn("user");

        assertTrue(searchService.quick("nguyen", 5, authentication).isEmpty());
        verify(searchRepository, never()).rows(anyString(), anyMap(), anyInt());
    }

    @Test
    void dioceseAdminLeaderSearchIsScopedByAccountDioceseAndNormalizedQuery() {
        UUID dioceseId = UUID.randomUUID();
        when(securityUtils.currentUsername(authentication)).thenReturn("admin");
        when(securityUtils.hasAuthority(authentication, "organization.leader.read.diocese")).thenReturn(true);
        when(accountRepository.findActiveDioceseIdByUsername("admin")).thenReturn(Optional.of(dioceseId));
        when(searchRepository.rows(anyString(), anyMap(), eq(5)))
                .thenReturn(List.<Object[]>of(new Object[]{"1", "Phêrô Nguyễn Văn A", "Giáo xứ A - Giáo hạt B", true}));

        List<QuickSearchGroup> groups = searchService.quick("  NGUYỄN  Đức ", 5, authentication);

        assertEquals(1, groups.size());
        assertEquals("leader", groups.get(0).type());
        assertEquals("Phêrô Nguyễn Văn A", groups.get(0).items().get(0).title());
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(searchRepository).rows(sql.capture(), params.capture(), eq(5));
        assertEquals("nguyen duc", params.getValue().get("q"));
        assertEquals("%nguyen duc%", params.getValue().get("pattern"));
        assertEquals(dioceseId, params.getValue().get("scopeId"));
        assertTrue(sql.getValue().contains("de.diocese_id = :scopeId"));
    }

    @Test
    void limitIsCappedAndAllScopeDoesNotBindScopeId() {
        when(securityUtils.currentUsername(authentication)).thenReturn("root");
        when(securityUtils.hasAuthority(authentication, "organization.diocese.read.all")).thenReturn(true);
        when(searchRepository.rows(anyString(), anyMap(), eq(10)))
                .thenReturn(List.<Object[]>of(new Object[]{"2", "Giáo phận X", "Liên đoàn", true}));

        searchService.quick("giao", 99, authentication);

        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(searchRepository).rows(anyString(), params.capture(), eq(10));
        assertTrue(!params.getValue().containsKey("scopeId"));
    }

    @Test
    void normalizerStripsDiacriticsAndEscapesLikeWildcards() {
        assertEquals("doan thieu nhi", SearchTextNormalizer.normalize("Đoàn  Thiếu Nhi"));
        assertEquals("100\\%\\_x", SearchTextNormalizer.escapeLike("100%_x"));
    }
}
