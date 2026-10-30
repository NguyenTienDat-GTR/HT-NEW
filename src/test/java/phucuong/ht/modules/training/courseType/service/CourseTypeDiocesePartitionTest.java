package phucuong.ht.modules.training.courseType.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.courseType.model.request.TrainingCourseTypeCreateRequest;
import phucuong.ht.modules.training.courseType.model.request.TrainingCourseTypeUpdateRequest;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;
import phucuong.ht.modules.training.courseType.security.CourseTypeDioceseGuard;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Danh mục loại khóa đi riêng theo giáo phận: không thấy, không dùng, không sửa loại khóa của giáo phận khác. */
@ExtendWith(MockitoExtension.class)
class CourseTypeDiocesePartitionTest {

    private static final UUID MINE = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Mock private TrainingCourseTypeRepository repository;
    @Mock private TrainingCourseRepository courseRepository;
    @Mock private LeaderLevelService leaderLevelService;

    private TrainingCourseTypeService service;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        CommonValidationHelper validation = new CommonValidationHelper(new securityUtils());
        service = new TrainingCourseTypeService(repository, courseRepository, validation, leaderLevelService,
                new CourseTypeDioceseGuard(leaderLevelService, validation),
                org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("phucuongadmin", null, List.of("ADMIN_DIOCESE"),
                List.of("training.course_type.read.diocese", "training.course_type.create.diocese"));
        authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(leaderLevelService.activeDioceseId(authentication)).thenReturn(Optional.of(MINE));
    }

    private TrainingCourseType type(String code, UUID dioceseId) {
        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode(code);
        type.setDioceseId(dioceseId);
        type.setManagementUnitType(UnitType.DIOCESE);
        return type;
    }

    @Test
    void createStoresTheActorsDioceseOnTheNewType() {
        TrainingCourseTypeCreateRequest request = new TrainingCourseTypeCreateRequest();
        request.setTypeName("Sứ mệnh tông đồ");
        request.setManagementUnitType(UnitType.DIOCESE);

        service.create(request, authentication);

        ArgumentCaptor<TrainingCourseType> saved = ArgumentCaptor.forClass(TrainingCourseType.class);
        verify(repository).save(saved.capture());
        assertEquals(MINE, saved.getValue().getDioceseId());
    }

    @Test
    void createRequiresAnAccountThatBelongsToADiocese() {
        when(leaderLevelService.activeDioceseId(authentication)).thenReturn(Optional.empty());
        TrainingCourseTypeCreateRequest request = new TrainingCourseTypeCreateRequest();
        request.setTypeName("Sứ mệnh tông đồ");
        request.setManagementUnitType(UnitType.DIOCESE);

        assertThrows(ApiException.class, () -> service.create(request, authentication));

        verify(repository, never()).save(any());
    }

    @Test
    void listIsFilteredToTheActorsDiocese() {
        when(repository.findAll(any(BaseSearchRequest.class))).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        service.findAll(new BaseSearchRequest(), authentication);

        ArgumentCaptor<BaseSearchRequest> scoped = ArgumentCaptor.forClass(BaseSearchRequest.class);
        verify(repository).findAll(scoped.capture());
        assertEquals(MINE, scoped.getValue().getFilters().get("dioceseId"));
    }

    @Test
    void typeOfAnotherDioceseCannotBeReadUpdatedOrToggled() {
        when(repository.findByCodeWithPrerequisite("X")).thenReturn(Optional.of(type("X", OTHER)));

        assertThrows(ApiException.class, () -> service.findById("X", authentication));
        assertThrows(ApiException.class, () -> service.update("X", new TrainingCourseTypeUpdateRequest(), authentication));
        assertThrows(ApiException.class, () -> service.toggleStatus("X",
                new phucuong.ht.common.model.request.StatusToggleRequest(), authentication));
        verify(repository, never()).toggleStatus(any(), any());
    }

    @Test
    void prerequisiteMustBelongToTheSameDiocese() {
        when(repository.findByCodeWithPrerequisite("MINE")).thenReturn(Optional.of(type("MINE", MINE)));
        when(repository.findByCodeWithPrerequisite("FOREIGN")).thenReturn(Optional.of(type("FOREIGN", OTHER)));
        TrainingCourseTypeUpdateRequest request = new TrainingCourseTypeUpdateRequest();
        request.setPrerequisiteTypeCode("FOREIGN");

        assertThrows(ApiException.class, () -> service.update("MINE", request, authentication));
    }
}
