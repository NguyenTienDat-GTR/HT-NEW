package phucuong.ht.modules.leaderLevel.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.leaderLevel.model.entity.LeaderLevel;
import phucuong.ht.modules.leaderLevel.model.request.LeaderLevelReorderRequest;
import phucuong.ht.modules.leaderLevel.model.request.LeaderLevelRequest;
import phucuong.ht.modules.leaderLevel.repository.LeaderLevelRepository;
import phucuong.ht.modules.leaderLevel.repository.LeaderLevelRepository.Usage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaderLevelServiceTest {

    private LeaderLevelRepository repository;
    private AccountRepository accountRepository;
    private LeaderLevelService service;
    private final UUID diocese = UUID.randomUUID();
    private final UUID otherDiocese = UUID.randomUUID();
    private final Authentication authentication = new UsernamePasswordAuthenticationToken("admin-diocese", null, List.of(() -> "ROLE_ADMIN_DIOCESE"));
    private List<LeaderLevel> levels;

    @BeforeEach
    void setUp() {
        repository = mock(LeaderLevelRepository.class);
        accountRepository = mock(AccountRepository.class);
        securityUtils securityUtils = new securityUtils();
        service = new LeaderLevelService(repository, new CommonValidationHelper(securityUtils), securityUtils, accountRepository, org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
        when(accountRepository.findActiveDioceseIdByUsername("admin-diocese")).thenReturn(Optional.of(diocese));
        levels = List.of(
                level("NONE", "Chưa có cấp", 0, true),
                level("DU_TRUONG", "Dự trưởng", 1, false),
                level("HT_I", "Huynh trưởng cấp I", 2, false),
                level("HT_II", "Huynh trưởng cấp II", 3, false),
                level("HT_III", "Huynh trưởng cấp III", 4, false));
        when(repository.findAllOrdered(diocese)).thenReturn(levels);
        when(repository.findAllOrdered(otherDiocese)).thenReturn(List.of());
        for (LeaderLevel level : levels) {
            when(repository.findByCode(diocese, level.getLevelCode())).thenReturn(Optional.of(level));
        }
        when(repository.hasAny(diocese)).thenReturn(true);
        when(repository.usageByCode(diocese)).thenReturn(new HashMap<>());
        when(repository.maxOrder(diocese)).thenReturn(4);
    }

    private LeaderLevel level(String code, String name, int order, boolean system) {
        LeaderLevel level = new LeaderLevel();
        level.setDioceseId(diocese);
        level.setLevelCode(code);
        level.setLevelName(name);
        level.setDisplayOrder(order);
        level.setIsSystem(system);
        level.setStatus(Boolean.TRUE);
        return level;
    }

    @Test
    void comparesLevelsByDisplayOrder() {
        var order = service.order(diocese);

        assertTrue(order.higher("HT_II", "HT_I"));
        assertFalse(order.higher("HT_I", "HT_I"));
        assertTrue(order.atMost("HT_I", "HT_II"));
        // Mã null hoặc không có trong danh mục coi như thấp hơn mọi cấp.
        assertTrue(order.higher("DU_TRUONG", null));
        assertEquals("Dự trưởng", order.nameOf("DU_TRUONG"));
    }

    @Test
    void eachDioceseHasItsOwnCatalog() {
        // Giáo phận khác chưa có cấp nào thì không thấy cấp của giáo phận này.
        assertEquals(-1, service.order(otherDiocese).rank("HT_I"));
        assertEquals(-1, service.order(null).rank("HT_I"));
        assertThrows(ApiException.class, () -> service.requireExistingOrNull("HT_I", "Cấp", otherDiocese));
        assertEquals("HT_I", service.requireExistingOrNull("HT_I", "Cấp", diocese));
        // Không xác định được giáo phận thì chỉ chuẩn hóa mã.
        assertEquals("HT_I", service.requireExistingOrNull(" HT_I ", "Cấp", null));
    }

    @Test
    void previousLevelIsTheRequirementForTheNextOne() {
        assertEquals(List.of("DU_TRUONG"), service.previousOf(diocese, "HT_I"));
        assertEquals(List.of("HT_I"), service.previousOf(diocese, "HT_II"));
        assertEquals(List.of(), service.previousOf(diocese, "NONE"));
        assertEquals(List.of("DU_TRUONG"), service.list(authentication).get(2).getPreviousCodes());
    }

    @Test
    void achievableLevelMustBeAboveCurrentAndNotAboveCourseLevel() {
        // Dự trưởng tham gia khóa HT cấp II: được chọn HT cấp I hoặc cấp II.
        assertEquals("HT_I", service.requireAchievable("HT_I", "DU_TRUONG", "HT_II", diocese));
        assertEquals("HT_II", service.requireAchievable("HT_II", "DU_TRUONG", "HT_II", diocese));
        // Không cao hơn cấp của khóa.
        assertThrows(ApiException.class, () -> service.requireAchievable("HT_III", "DU_TRUONG", "HT_II", diocese));
        // Phải cao hơn cấp hiện tại.
        assertThrows(ApiException.class, () -> service.requireAchievable("DU_TRUONG", "DU_TRUONG", "HT_II", diocese));
        assertThrows(ApiException.class, () -> service.requireAchievable("HT_I", "HT_II", "HT_III", diocese));
        // Bắt buộc chọn, phải tồn tại trong danh mục của giáo phận.
        assertThrows(ApiException.class, () -> service.requireAchievable(" ", "DU_TRUONG", "HT_II", diocese));
        assertThrows(ApiException.class, () -> service.requireAchievable("KHONG_CO", "DU_TRUONG", "HT_II", diocese));
        assertThrows(ApiException.class, () -> service.requireAchievable("HT_I", "DU_TRUONG", "HT_II", otherDiocese));
    }

    @Test
    void reorderRequiresExactlyTheMovableLevels() {
        LeaderLevelReorderRequest request = new LeaderLevelReorderRequest();
        // Thiếu một cấp.
        request.setTiers(List.of(List.of("HT_I"), List.of("DU_TRUONG"), List.of("HT_II")));
        assertThrows(ApiException.class, () -> service.reorder(request, authentication));
        // Bậc rỗng.
        request.setTiers(List.of(List.of("HT_III"), List.of(), List.of("HT_II"), List.of("HT_I"), List.of("DU_TRUONG")));
        assertThrows(ApiException.class, () -> service.reorder(request, authentication));
        // Một cấp xuất hiện hai lần.
        request.setTiers(List.of(List.of("HT_III", "HT_III"), List.of("HT_II"), List.of("HT_I"), List.of("DU_TRUONG")));
        assertThrows(ApiException.class, () -> service.reorder(request, authentication));

        request.setTiers(List.of(List.of("HT_III"), List.of("HT_II"), List.of("HT_I"), List.of("DU_TRUONG")));
        service.reorder(request, authentication);

        assertEquals(1, levels.get(4).getDisplayOrder());
        assertEquals(4, levels.get(1).getDisplayOrder());
        // Cấp hệ thống giữ thứ tự 0.
        assertEquals(0, levels.get(0).getDisplayOrder());
    }

    @Test
    void levelsInTheSameTierAreEqualAndShareTheSameRequirement() {
        LeaderLevelReorderRequest request = new LeaderLevelReorderRequest();
        // Dự trưởng < (HT cấp I = HT cấp II) < HT cấp III.
        request.setTiers(List.of(List.of("DU_TRUONG"), List.of("HT_I", "HT_II"), List.of("HT_III")));

        service.reorder(request, authentication);

        assertEquals(1, levels.get(1).getDisplayOrder());
        assertEquals(2, levels.get(2).getDisplayOrder());
        assertEquals(2, levels.get(3).getDisplayOrder());
        assertEquals(3, levels.get(4).getDisplayOrder());
        var order = service.order(diocese);
        // Bằng cấp: không bên nào cao hơn bên nào, nhưng cùng cao hơn Dự trưởng và cùng thấp hơn HT cấp III.
        assertFalse(order.higher("HT_II", "HT_I"));
        assertFalse(order.higher("HT_I", "HT_II"));
        assertTrue(order.higher("HT_I", "DU_TRUONG"));
        assertTrue(order.higher("HT_III", "HT_II"));
        // Cấp cùng bậc cùng có điều kiện là bậc ngay trước.
        var responses = service.list(authentication);
        assertEquals(List.of("DU_TRUONG"), responses.get(2).getPreviousCodes());
        assertEquals(List.of("DU_TRUONG"), responses.get(3).getPreviousCodes());
        assertEquals(List.of("HT_I", "HT_II"), responses.get(4).getPreviousCodes());
    }

    @Test
    void firstTierCanBeEqualToTheStartingLevel() {
        LeaderLevelReorderRequest request = new LeaderLevelReorderRequest();
        // Dự trưởng bằng cấp với cấp khởi đầu (Chưa có cấp), rồi HT I, HT II, HT III cao dần.
        request.setTiers(List.of(List.of("DU_TRUONG"), List.of("HT_I"), List.of("HT_II"), List.of("HT_III")));
        request.setFirstTierBase(true);

        service.reorder(request, authentication);

        // Cấp hệ thống và bậc thấp nhất cùng bậc 0 nên bằng cấp nhau.
        assertEquals(0, levels.get(0).getDisplayOrder());
        assertEquals(0, levels.get(1).getDisplayOrder());
        assertEquals(1, levels.get(2).getDisplayOrder());
        var order = service.order(diocese);
        assertFalse(order.higher("DU_TRUONG", "NONE"));
        assertFalse(order.higher("NONE", "DU_TRUONG"));
        assertTrue(order.higher("HT_I", "DU_TRUONG"));
        // Bậc thấp nhất bằng cấp khởi đầu nên không cần cấp trước.
        assertEquals(List.of(), service.previousOf(diocese, "DU_TRUONG"));
    }

    @Test
    void systemAndUsedLevelsCannotBeDeleted() {
        assertThrows(ApiException.class, () -> service.delete("NONE", authentication));

        when(repository.usageByCode(diocese)).thenReturn(Map.of("HT_I", new Usage(3, 1, 0, 0)));
        ApiException error = assertThrows(ApiException.class, () -> service.delete("HT_I", authentication));
        assertTrue(error.getMessage().contains("đang được sử dụng"));
        verify(repository, never()).remove(any(), any());

        service.delete("HT_III", authentication);
        verify(repository).remove(diocese, "HT_III");
    }

    @Test
    void accountWithoutDioceseCannotManageLevels() {
        Authentication nobody = new UsernamePasswordAuthenticationToken("no-diocese", null, List.of(() -> "ROLE_X"));
        when(accountRepository.findActiveDioceseIdByUsername("no-diocese")).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.list(nobody));
        assertThrows(ApiException.class, () -> service.delete("HT_III", nobody));
    }

    @Test
    void createsLevelOnTopOfTheActorDioceseWithCodeDerivedFromName() {
        LeaderLevelRequest request = new LeaderLevelRequest();
        request.setLevelName("Huấn luyện viên cấp IV");
        when(repository.persist(any())).thenAnswer(invocation -> invocation.getArgument(0));
        List<LeaderLevel> withNew = new ArrayList<>(levels);
        LeaderLevel created = level("HUAN_LUYEN_VIEN_CAP_IV", "Huấn luyện viên cấp IV", 5, false);
        withNew.add(created);
        // Lần đọc đầu (kiểm tra trùng tên) chưa có cấp mới; các lần sau (dựng kết quả) đã có.
        when(repository.findAllOrdered(diocese)).thenReturn(levels, withNew);
        when(repository.findByCode(diocese, "HUAN_LUYEN_VIEN_CAP_IV")).thenReturn(Optional.empty(), Optional.of(created));

        var response = service.create(request, authentication);

        assertEquals("HUAN_LUYEN_VIEN_CAP_IV", response.getLevelCode());
        assertEquals(5, response.getDisplayOrder());
        assertEquals(List.of("HT_III"), response.getPreviousCodes());
        ArgumentCaptor<LeaderLevel> saved = ArgumentCaptor.forClass(LeaderLevel.class);
        verify(repository).persist(saved.capture());
        assertEquals(diocese, saved.getValue().getDioceseId());
    }

    @Test
    void duplicateCodeOrNameIsRejectedWithinTheDiocese() {
        LeaderLevelRequest request = new LeaderLevelRequest();
        request.setLevelCode("HT_I");
        request.setLevelName("Tên mới");
        assertThrows(ApiException.class, () -> service.create(request, authentication));

        LeaderLevelRequest sameName = new LeaderLevelRequest();
        sameName.setLevelCode("MOI");
        sameName.setLevelName("dự trưởng");
        when(repository.findByCode(diocese, "MOI")).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.create(sameName, authentication));
    }

    @Test
    void newDioceseGetsTheDefaultCatalogOnlyOnce() {
        when(repository.hasAny(otherDiocese)).thenReturn(false);

        service.seedDefaults(otherDiocese, "admin");

        ArgumentCaptor<LeaderLevel> saved = ArgumentCaptor.forClass(LeaderLevel.class);
        verify(repository, times(9)).persist(saved.capture());
        assertEquals("NONE", saved.getAllValues().get(0).getLevelCode());
        assertTrue(saved.getAllValues().get(0).getIsSystem());
        assertEquals(8, saved.getAllValues().get(8).getDisplayOrder());
        assertEquals(otherDiocese, saved.getAllValues().get(5).getDioceseId());

        // Giáo phận đã có danh mục thì không tạo thêm.
        service.seedDefaults(diocese, "admin");
        verify(repository, times(9)).persist(any());
    }
}
