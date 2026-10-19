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
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.model.request.TrainingCourseUpdateRequest;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;
import phucuong.ht.modules.training.trainingCourse.security.TrainingCourseAccessEvaluator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingCourseUpdateScopeTest {

    @Mock private phucuong.ht.modules.training.scoreComponentTemplate.repository.ScoreComponentTemplateRepository scoreComponentTemplateRepository;
    @Mock private phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver notificationRecipientResolver;
    @Mock private phucuong.ht.modules.auth.repository.AccountRepository accountRepository;
    @Mock private TrainingCourseRepository trainingCourseRepository;
    @Mock private TrainingCourseTypeRepository trainingCourseTypeRepository;
    @Mock private CourseParticipationRepository courseParticipationRepository;
    @Mock private DioceseRepository dioceseRepository;
    @Mock private DeaneryRepository deaneryRepository;
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

    @Test
    void registrationCanBeClosedAndReopenedWhileWindowIsStillRunning() {
        TrainingCourse course = deaneryCourse("DT-3", UUID.randomUUID());
        course.setRegistrationEndAt(java.time.OffsetDateTime.now().plusDays(1));
        var authentication = authentication("admin-deanery", "training.course.update.deanery");
        when(trainingCourseRepository.findByCodeWithType("DT-3")).thenReturn(Optional.of(course));

        var request = new phucuong.ht.modules.training.trainingCourse.model.request.CourseRegistrationToggleRequest();
        request.setOpen(false);
        assertEquals(false, service().setRegistrationOpen("DT-3", request, authentication).getRegistrationOpen());
        request.setOpen(true);
        assertEquals(true, service().setRegistrationOpen("DT-3", request, authentication).getRegistrationOpen());
    }

    @Test
    void registrationCannotBeReopenedAfterWindowEnded() {
        TrainingCourse course = deaneryCourse("DT-4", UUID.randomUUID());
        course.setRegistrationEndAt(java.time.OffsetDateTime.now().minusMinutes(1));
        var authentication = authentication("admin-deanery", "training.course.update.deanery");
        when(trainingCourseRepository.findByCodeWithType("DT-4")).thenReturn(Optional.of(course));

        var request = new phucuong.ht.modules.training.trainingCourse.model.request.CourseRegistrationToggleRequest();
        request.setOpen(true);
        assertThrows(RuntimeException.class, () -> service().setRegistrationOpen("DT-4", request, authentication));
    }

    @Test
    void passingScoreMustNotExceedWeightedMaxOfIncludedComponents() {
        UUID deaneryId = UUID.randomUUID();
        TrainingCourse course = deaneryCourse("DT-5", deaneryId);
        var authentication = authentication("admin-deanery", "training.course.update.deanery");
        TrainingCourseUpdateRequest request = new TrainingCourseUpdateRequest();
        request.setPassingScore(java.math.BigDecimal.valueOf(9));

        // Hai thành phần tối đa 10 (hệ số 1) và 5 (hệ số 1, tính vào tổng kết), 20 không tính -> giới hạn 7.5
        when(trainingCourseRepository.findByCodeWithType("DT-5")).thenReturn(Optional.of(course));
        when(accountScopeValidationHelper.requireActiveDeaneryId(any(), eq(TrainingCourseAccessEvaluator.UPDATE_DEANERY))).thenReturn(deaneryId);
        when(courseParticipationRepository.existsByCourseCode("DT-5")).thenReturn(false);
        when(deaneryRepository.getRequired(deaneryId)).thenReturn(course.getDeanery());
        when(accountScopeValidationHelper.requireActiveDeaneryId(any(), eq(TrainingCourseAccessEvaluator.CREATE_DEANERY))).thenReturn(deaneryId);
        when(scoreComponentTemplateRepository.findActiveForCourse(eq("SMTL_DT"), any(), eq(deaneryId)))
                .thenReturn(List.of(component(10, true), component(5, true), component(20, false)));

        assertThrows(RuntimeException.class, () -> service().update("DT-5", request, authentication));
        request.setPassingScore(java.math.BigDecimal.valueOf(7.5));
        assertEquals(0, java.math.BigDecimal.valueOf(7.5).compareTo(service().update("DT-5", request, authentication).getPassingScore()));
    }

    private phucuong.ht.modules.training.scoreComponentTemplate.model.entity.ScoreComponentTemplate component(int max, boolean included) {
        var component = new phucuong.ht.modules.training.scoreComponentTemplate.model.entity.ScoreComponentTemplate();
        component.setMaxScore(java.math.BigDecimal.valueOf(max));
        component.setCoefficient(java.math.BigDecimal.ONE);
        component.setIncludeInFinal(included);
        return component;
    }

    private TrainingCourseService service() {
        return new TrainingCourseService(
                trainingCourseRepository,
                trainingCourseTypeRepository,
                courseParticipationRepository,
                dioceseRepository,
                deaneryRepository,
                new TrainingWorkflowSupport(),
                accountScopeValidationHelper,
                new CommonValidationHelper(new securityUtils()),
                new securityUtils(),
                scoreComponentTemplateRepository,
                notificationRecipientResolver,
                accountRepository
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
