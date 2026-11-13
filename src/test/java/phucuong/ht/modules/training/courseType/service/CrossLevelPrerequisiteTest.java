package phucuong.ht.modules.training.courseType.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.courseType.model.request.TrainingCourseTypeCreateRequest;
import phucuong.ht.modules.training.courseType.model.request.TrainingCourseTypeUpdateRequest;
import phucuong.ht.modules.training.courseType.model.response.PrerequisiteOptionResponse;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;
import phucuong.ht.modules.training.courseType.security.CourseTypeDioceseGuard;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Khóa tiên quyết liên cấp: loại khóa giáo phận chọn được loại khóa giáo hạt, loại khóa giáo hạt chỉ chọn cấp giáo hạt. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrossLevelPrerequisiteTest {

    private static final UUID DIOCESE = UUID.randomUUID();

    @Mock private TrainingCourseTypeRepository repository;
    @Mock private TrainingCourseRepository courseRepository;
    @Mock private LeaderLevelService leaderLevelService;
    @Mock private phucuong.ht.modules.auth.repository.AccountRepository accountRepository;
    private phucuong.ht.modules.training.courseType.security.CourseTypeOwnership ownership;

    private TrainingCourseTypeService service;
    private Authentication authentication;

    private final TrainingCourseType deaneryLevel1 = type("HT1_GH", "Huấn luyện cấp 1", UnitType.DEANERY, true, null);
    private final TrainingCourseType dioceseLevel2 = type("HT2_GP", "Huấn luyện cấp 2", UnitType.DIOCESE, true, deaneryLevel1);
    private final TrainingCourseType dioceseBasic = type("DB_GP", "Dự bị", UnitType.DIOCESE, true, null);
    private final TrainingCourseType deaneryOwn = ownedBy(type("OWN_GH", "Khóa giáo hạt tự tạo", UnitType.DEANERY, true, null));
    private final TrainingCourseType deaneryOld = type("CU_GH", "Khóa cũ", UnitType.DEANERY, false, null);

    @BeforeEach
    void setUp() {
        CommonValidationHelper validation = new CommonValidationHelper(new securityUtils());
        ownership = new phucuong.ht.modules.training.courseType.security.CourseTypeOwnership(new securityUtils(), accountRepository, validation);
        service = new TrainingCourseTypeService(repository, courseRepository, validation, leaderLevelService,
                new CourseTypeDioceseGuard(leaderLevelService, validation, ownership),
                org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class),
                ownership);
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("admin", null, List.of("ADMIN_DIOCESE"),
                List.of("training.course_type.create.diocese", "training.course_type.update.diocese"));
        authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(leaderLevelService.activeDioceseId(authentication)).thenReturn(Optional.of(DIOCESE));
        when(accountRepository.findActiveDioceseIdByUsername("admin")).thenReturn(Optional.of(DIOCESE));
        when(accountRepository.findActiveDioceseIdByUsername("admin")).thenReturn(Optional.of(DIOCESE));
        when(leaderLevelService.order(DIOCESE)).thenReturn(new LeaderLevelService.LevelOrder(Map.of(), Map.of("C1", "Cấp 1")));
        for (TrainingCourseType item : List.of(deaneryLevel1, dioceseLevel2, dioceseBasic, deaneryOld, deaneryOwn)) {
            when(repository.findByCodeWithPrerequisite(item.getTypeCode())).thenReturn(Optional.of(item));
        }
        when(repository.findAllInDioceseWithPrerequisite(DIOCESE)).thenReturn(List.of(dioceseBasic, deaneryLevel1, dioceseLevel2, deaneryOld, deaneryOwn));
        when(repository.countCoursesByType(DIOCESE)).thenReturn(Map.of("HT1_GH", 5L));
        when(repository.countCompletedLeadersByType(DIOCESE)).thenReturn(Map.of("HT1_GH", 42L));
    }

    @Test
    void dioceseTypeCanUseDeaneryTypeAsPrerequisite() {
        var request = createRequest(UnitType.DIOCESE, "HT1_GH");
        var response = service.create(request, authentication);
        assertEquals("HT1_GH", response.getPrerequisiteTypeCode());
        assertEquals(UnitType.DEANERY, response.getPrerequisiteManagementUnitType());
    }

    @Test
    void deaneryTypeCannotUseDioceseTypeAsPrerequisite() {
        var request = createRequest(UnitType.DEANERY, "DB_GP");
        ApiException error = assertThrows(ApiException.class, () -> service.create(request, authentication));
        assertTrue(error.getMessage().contains("chỉ được chọn khóa tiên quyết cấp giáo hạt"));
    }

    @Test
    void inactiveTypeCannotBeChosenAsNewPrerequisite() {
        var request = createRequest(UnitType.DIOCESE, "CU_GH");
        ApiException error = assertThrows(ApiException.class, () -> service.create(request, authentication));
        assertTrue(error.getMessage().contains("ngừng hoạt động"));
    }

    @Test
    void inactiveCurrentPrerequisiteCanBeKeptOnEdit() {
        TrainingCourseType editing = type("X_GP", "Khóa X", UnitType.DIOCESE, true, deaneryOld);
        when(repository.findByCodeWithPrerequisite("X_GP")).thenReturn(Optional.of(editing));
        var request = new TrainingCourseTypeUpdateRequest();
        request.setPrerequisiteTypeCode("CU_GH");
        assertEquals("CU_GH", service.update("X_GP", request, authentication).getPrerequisiteTypeCode());
    }

    @Test
    void prerequisiteCycleIsRejected() {
        // Cấp 2 đang cần cấp 1; sửa cấp 1 để cần cấp 2 sẽ tạo vòng lặp.
        var request = new TrainingCourseTypeUpdateRequest();
        request.setPrerequisiteTypeCode("HT2_GP");
        request.setManagementUnitType(UnitType.DIOCESE);
        ApiException error = assertThrows(ApiException.class, () -> service.update("HT1_GH", request, authentication));
        assertTrue(error.getMessage().contains("vòng lặp"));
    }

    @Test
    void typeUsedByDeaneryTypeCannotMoveToDioceseLevel() {
        when(repository.existsDeaneryTypeWithPrerequisite("HT1_GH")).thenReturn(true);
        var request = new TrainingCourseTypeUpdateRequest();
        request.setManagementUnitType(UnitType.DIOCESE);
        ApiException error = assertThrows(ApiException.class, () -> service.update("HT1_GH", request, authentication));
        assertTrue(error.getMessage().contains("Không thể đổi sang cấp giáo phận"));
    }

    @Test
    void dioceseOwnerSeesBothLevelsActiveOnly() {
        List<PrerequisiteOptionResponse> options = service.prerequisiteOptions(UnitType.DIOCESE, null, authentication);
        assertEquals(List.of("DB_GP", "HT1_GH", "HT2_GP"), options.stream().map(PrerequisiteOptionResponse::getTypeCode).toList());
        PrerequisiteOptionResponse level1 = options.get(1);
        assertEquals(5L, level1.getCourseCount());
        assertEquals(42L, level1.getCompletedLeaderCount());
        assertTrue(level1.isSelectable());
    }

    @Test
    void deaneryOwnerSeesOnlyDeaneryTypes() {
        List<PrerequisiteOptionResponse> options = service.prerequisiteOptions(UnitType.DEANERY, null, authentication);
        assertEquals(List.of("HT1_GH"), options.stream().map(PrerequisiteOptionResponse::getTypeCode).toList());
    }

    @Test
    void editingExcludesItselfAndMarksCycleAsNotSelectable() {
        List<PrerequisiteOptionResponse> options = service.prerequisiteOptions(null, "HT1_GH", authentication);
        // Cấp 1 là giáo hạt: chỉ thấy loại khóa giáo hạt khác (đang hoạt động) -> không còn gì; đổi sang chọn như cấp giáo phận để kiểm tra vòng lặp.
        assertTrue(options.isEmpty());
        List<PrerequisiteOptionResponse> asDiocese = service.prerequisiteOptions(UnitType.DIOCESE, "HT1_GH", authentication);
        PrerequisiteOptionResponse level2 = asDiocese.stream().filter(item -> item.getTypeCode().equals("HT2_GP")).findFirst().orElseThrow();
        assertFalse(level2.isSelectable());
        assertFalse(asDiocese.stream().anyMatch(item -> item.getTypeCode().equals("HT1_GH")));
    }

    @Test
    void editingKeepsInactiveCurrentPrerequisiteVisible() {
        TrainingCourseType editing = type("X_GP", "Khóa X", UnitType.DIOCESE, true, deaneryOld);
        when(repository.findByCodeWithPrerequisite("X_GP")).thenReturn(Optional.of(editing));
        List<PrerequisiteOptionResponse> options = service.prerequisiteOptions(null, "X_GP", authentication);
        PrerequisiteOptionResponse old = options.stream().filter(item -> item.getTypeCode().equals("CU_GH")).findFirst().orElseThrow();
        assertTrue(old.isCurrent());
    }

    @Test
    void dioceseCannotPickTypeOwnedByADeanery() {
        var request = createRequest(UnitType.DIOCESE, "OWN_GH");
        ApiException error = assertThrows(ApiException.class, () -> service.create(request, authentication));
        assertTrue(error.getMessage().contains("do giáo phận tạo"));
        assertFalse(service.prerequisiteOptions(UnitType.DIOCESE, null, authentication).stream().anyMatch(item -> item.getTypeCode().equals("OWN_GH")));
    }

    private static TrainingCourseType ownedBy(TrainingCourseType type) {
        type.setOwnerUnitType(UnitType.DEANERY);
        type.setOwnerDeaneryId(UUID.randomUUID());
        return type;
    }

    @Test
    void clearingCompletionLevelStopsPromotionAndWinsOverASubmittedLevel() {
        dioceseBasic.setResultingLevel("C1");
        when(leaderLevelService.requireExistingOrNull("C1", "Cấp độ đạt được", DIOCESE)).thenReturn("C1");
        var keep = new TrainingCourseTypeUpdateRequest();
        keep.setTypeName("Đổi tên");
        assertEquals("C1", service.update("DB_GP", keep, authentication).getResultingLevel(), "không gửi cấp độ thì giữ giá trị đã lưu");

        var clear = new TrainingCourseTypeUpdateRequest();
        clear.setClearResultingLevel(true);
        clear.setResultingLevel("C1");
        assertNull(service.update("DB_GP", clear, authentication).getResultingLevel());
    }

    @Test
    void zeroMinimumAgeMeansNoRequirementAndNegativeIsRejected() {
        var create = createRequest(UnitType.DIOCESE, null);
        create.setRequiredMinAge(0);
        assertNull(service.create(create, authentication).getRequiredMinAge());

        dioceseBasic.setRequiredMinAge(18);
        var update = new TrainingCourseTypeUpdateRequest();
        update.setRequiredMinAge(0);
        assertNull(service.update("DB_GP", update, authentication).getRequiredMinAge());

        var negative = new TrainingCourseTypeUpdateRequest();
        negative.setRequiredMinAge(-1);
        ApiException error = assertThrows(ApiException.class, () -> service.update("DB_GP", negative, authentication));
        assertTrue(error.getMessage().contains("Tuổi tối thiểu"));
    }

    @Test
    void updateLooksUpTheActorDioceseOnlyOnce() {
        when(leaderLevelService.requireExistingOrNull("C1", "Cấp yêu cầu", DIOCESE)).thenReturn("C1");
        when(leaderLevelService.requireExistingOrNull("C1", "Cấp độ đạt được", DIOCESE)).thenReturn("C1");
        var request = new TrainingCourseTypeUpdateRequest();
        request.setRequiredCurrentLevel("C1");
        request.setResultingLevel("C1");
        service.update("DB_GP", request, authentication);
        org.mockito.Mockito.verify(leaderLevelService, org.mockito.Mockito.times(1)).activeDioceseId(authentication);
    }

    private static TrainingCourseTypeCreateRequest createRequest(UnitType level, String prerequisiteCode) {
        var request = new TrainingCourseTypeCreateRequest();
        request.setTypeName("Khóa mới");
        request.setManagementUnitType(level);
        request.setPrerequisiteTypeCode(prerequisiteCode);
        return request;
    }

    private static TrainingCourseType type(String code, String name, UnitType level, boolean active, TrainingCourseType prerequisite) {
        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode(code);
        type.setTypeName(name);
        type.setDioceseId(DIOCESE);
        type.setManagementUnitType(level);
        type.setStatus(active);
        type.setPrerequisiteType(prerequisite);
        return type;
    }
}
