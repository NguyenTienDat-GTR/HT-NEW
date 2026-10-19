package phucuong.ht.modules.training.trainingCourse.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;
import phucuong.ht.modules.training.scoreFormulaTemplate.repository.ScoreFormulaTemplateRepository;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.model.request.TrainingCourseUpdateRequest;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;
import phucuong.ht.modules.training.trainingCourse.security.TrainingCourseAccessEvaluator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingCourseUpdateScopeTest {

    @Mock private TrainingCourseRepository trainingCourseRepository;
    @Mock private TrainingCourseTypeRepository trainingCourseTypeRepository;
    @Mock private CourseParticipationRepository courseParticipationRepository;
    @Mock private DioceseRepository dioceseRepository;
    @Mock private DeaneryRepository deaneryRepository;
    @Mock private ScoreFormulaTemplateRepository scoreFormulaTemplateRepository;
    @Mock private AccountScopeValidationHelper accountScopeValidationHelper;

    @Test
    void deaneryUpdaterCannotConvertOwnDtCourseIntoDioceseCourseWithoutDioceseUpdateAuthority() {
        UUID deaneryId = UUID.randomUUID();
        TrainingCourse course = deaneryCourse("DT-1", deaneryId);
        var authentication = authentication("admin-deanery", "training.course.update.deanery");
        TrainingCourseUpdateRequest request = new TrainingCourseUpdateRequest();
        request.setCourseTypeCode("SMTL_I");

        when(trainingCourseRepository.findByCodeWithType("DT-1")).thenReturn(Optional.of(course));
        when(trainingCourseTypeRepository.findByCodeWithPrerequisite("SMTL_I"))
                .thenReturn(Optional.of(courseType("SMTL_I", UnitType.DIOCESE)));
        when(accountScopeValidationHelper.requireActiveDeaneryId(
                eq(authentication), eq(TrainingCourseAccessEvaluator.UPDATE_DEANERY)))
                .thenReturn(deaneryId);

        assertThrows(RuntimeException.class, () -> service().update("DT-1", request, authentication));
    }

    @Test
    void deaneryUpdaterCanStillUpdateAnOwnDtCourseWithoutChangingItsOwnerType() {
        UUID deaneryId = UUID.randomUUID();
        TrainingCourse course = deaneryCourse("DT-2", deaneryId);
        var authentication = authentication("admin-deanery", "training.course.update.deanery");
        TrainingCourseUpdateRequest request = new TrainingCourseUpdateRequest();
        request.setCourseName("Updated DT course");

        when(trainingCourseRepository.findByCodeWithType("DT-2")).thenReturn(Optional.of(course));
        when(accountScopeValidationHelper.requireActiveDeaneryId(eq(authentication), anyString())).thenReturn(deaneryId);
        when(deaneryRepository.getRequired(deaneryId)).thenReturn(course.getDeanery());

        var response = service().update("DT-2", request, authentication);

        assertEquals("Updated DT course", response.getCourseName());
        assertEquals("SMTL_DT", response.getCourseTypeCode());
        assertEquals(deaneryId, response.getDeaneryId());
    }

    private TrainingCourseService service() {
        return new TrainingCourseService(
                trainingCourseRepository,
                trainingCourseTypeRepository,
                courseParticipationRepository,
                dioceseRepository,
                deaneryRepository,
                scoreFormulaTemplateRepository,
                new TrainingWorkflowSupport(),
                accountScopeValidationHelper,
                new CommonValidationHelper(new securityUtils()),
                new securityUtils()
        );
    }

    private TrainingCourseType courseType(String code, UnitType managementUnitType) {
        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode(code);
        type.setTypeName(code);
        type.setManagementUnitType(managementUnitType);
        type.setStatus(Boolean.TRUE);
        return type;
    }

    private TrainingCourse deaneryCourse(String code, UUID deaneryId) {
        Diocese diocese = new Diocese();
        diocese.setId(UUID.randomUUID());
        Deanery deanery = new Deanery();
        deanery.setId(deaneryId);
        deanery.setDiocese(diocese);
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode(code);
        course.setCourseName("DT course");
        course.setCourseType(courseType("SMTL_DT", UnitType.DEANERY));
        course.setDeanery(deanery);
        return course;
    }

    private UsernamePasswordAuthenticationToken authentication(String username, String... authorities) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, null, List.of("ADMIN_DEANERY"), List.of(authorities));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
