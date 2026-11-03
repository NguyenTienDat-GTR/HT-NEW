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
import phucuong.ht.common.exception.ApiException;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// Cờ bật điểm danh và cờ tự gửi email thông tin tham dự trên khóa huấn luyện.
@ExtendWith(MockitoExtension.class)
class TrainingCourseAttendanceFlagsTest {

    @Mock private phucuong.ht.modules.training.courseScoreComponent.repository.TrainingCourseScoreComponentRepository courseScoreComponentRepository;
    @Mock private phucuong.ht.modules.training.courseScoreComponent.service.CourseScoreComponentService courseScoreComponentService;
    @Mock private phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver notificationRecipientResolver;
    @Mock private phucuong.ht.modules.auth.repository.AccountRepository accountRepository;
    @Mock private phucuong.ht.config.DomainEventPublisher domainEventPublisher;
    @Mock private phucuong.ht.modules.auth.repository.AccountRoleRepository accountRoleRepository;
    @Mock private TrainingCourseRepository trainingCourseRepository;
    @Mock private TrainingCourseTypeRepository trainingCourseTypeRepository;
    @Mock private CourseParticipationRepository courseParticipationRepository;
    @Mock private DioceseRepository dioceseRepository;
    @Mock private DeaneryRepository deaneryRepository;
    @Mock private AccountScopeValidationHelper accountScopeValidationHelper;

    private final UUID deaneryId = UUID.randomUUID();

    private TrainingCourse givenCourse(String code) {
        TrainingCourse course = deaneryCourse(code);
        when(trainingCourseRepository.findByCodeWithType(code)).thenReturn(Optional.of(course));
        org.mockito.Mockito.lenient().when(accountScopeValidationHelper.requireActiveDeaneryId(any(), anyString())).thenReturn(deaneryId);
        org.mockito.Mockito.lenient().when(deaneryRepository.getRequired(deaneryId)).thenReturn(course.getDeanery());
        return course;
    }

    private UsernamePasswordAuthenticationToken auth() {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("admin-deanery", null, List.of("ADMIN_DEANERY"), List.of("training.course.update.deanery"));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void newCoursesStartWithBothFlagsOff() {
        TrainingCourse course = new TrainingCourse();
        assertFalse(course.getAttendanceEnabled());
        assertFalse(course.getAutoSendInvitation());
    }

    @Test
    void updateTurnsFlagsOnAndOffIndependently() {
        TrainingCourse course = givenCourse("DT-F1");

        TrainingCourseUpdateRequest attendanceOnly = new TrainingCourseUpdateRequest();
        attendanceOnly.setAttendanceEnabled(true);
        var first = service().update("DT-F1", attendanceOnly, auth());
        assertTrue(first.getAttendanceEnabled());
        assertFalse(first.getAutoSendInvitation());

        TrainingCourseUpdateRequest autoOnly = new TrainingCourseUpdateRequest();
        autoOnly.setAutoSendInvitation(true);
        var second = service().update("DT-F1", autoOnly, auth());
        assertTrue(second.getAttendanceEnabled());
        assertTrue(second.getAutoSendInvitation());

        TrainingCourseUpdateRequest off = new TrainingCourseUpdateRequest();
        off.setAttendanceEnabled(false);
        off.setAutoSendInvitation(false);
        var third = service().update("DT-F1", off, auth());
        assertFalse(third.getAttendanceEnabled());
        assertFalse(third.getAutoSendInvitation());
        assertFalse(course.getAttendanceEnabled());
    }

    @Test
    void omittedFlagsKeepTheirCurrentValues() {
        TrainingCourse course = givenCourse("DT-F2");
        course.setAttendanceEnabled(true);
        course.setAutoSendInvitation(true);

        TrainingCourseUpdateRequest rename = new TrainingCourseUpdateRequest();
        rename.setCourseName("Tên mới");
        var response = service().update("DT-F2", rename, auth());

        assertEquals("Tên mới", response.getCourseName());
        assertTrue(response.getAttendanceEnabled());
        assertTrue(response.getAutoSendInvitation());
    }

    @Test
    void autoSendCanBeOnWithoutAttendance() {
        givenCourse("DT-F3");
        TrainingCourseUpdateRequest request = new TrainingCourseUpdateRequest();
        request.setAutoSendInvitation(true);
        request.setAttendanceEnabled(false);
        var response = service().update("DT-F3", request, auth());
        assertTrue(response.getAutoSendInvitation());
        assertFalse(response.getAttendanceEnabled());
    }

    @Test
    void pastCoursesAreEditedElsewhereSoFlagsCannotBeChangedThroughTheRegularUpdate() {
        TrainingCourse past = givenCourse("DT-F4");
        past.setPastCourse(true);

        TrainingCourseUpdateRequest attendance = new TrainingCourseUpdateRequest();
        attendance.setAttendanceEnabled(true);
        attendance.setAutoSendInvitation(true);
        ApiException error = assertThrows(ApiException.class, () -> service().update("DT-F4", attendance, auth()));
        assertEquals(409, error.getStatus().value());
        assertFalse(past.getAttendanceEnabled());
        assertFalse(past.getAutoSendInvitation());
    }

    private TrainingCourseService service() {
        return new TrainingCourseService(
                trainingCourseRepository,
                trainingCourseTypeRepository,
                org.mockito.Mockito.mock(phucuong.ht.modules.training.courseType.security.CourseTypeDioceseGuard.class),
                courseParticipationRepository,
                dioceseRepository,
                deaneryRepository,
                new TrainingWorkflowSupport(),
                accountScopeValidationHelper,
                org.mockito.Mockito.mock(phucuong.ht.common.security.AccountScopeResolver.class),
                org.mockito.Mockito.mock(phucuong.ht.modules.training.RegistrationSenderResolver.class),
                new CommonValidationHelper(new securityUtils()),
                new securityUtils(),
                courseScoreComponentService,
                courseScoreComponentRepository,
                notificationRecipientResolver,
                accountRepository,
                domainEventPublisher,
                accountRoleRepository,
                org.mockito.Mockito.mock(phucuong.ht.modules.training.trainingCourse.service.CourseFinishService.class),
                org.mockito.Mockito.mock(phucuong.ht.modules.certificate.service.CertificateLifecycleService.class),
                org.mockito.Mockito.mock(phucuong.ht.modules.certificate.repository.CertificateRepository.class),
                org.mockito.Mockito.mock(phucuong.ht.modules.training.CourseActivityGuard.class),
                org.mockito.Mockito.mock(phucuong.ht.modules.leaderLevel.service.LeaderLevelService.class),
                org.mockito.Mockito.mock(phucuong.ht.modules.certificate.service.CertificateRealtimeNotifier.class),
                org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
    }

    private TrainingCourse deaneryCourse(String code) {
        Diocese diocese = new Diocese();
        diocese.setId(UUID.randomUUID());
        Deanery deanery = new Deanery();
        deanery.setId(deaneryId);
        deanery.setDiocese(diocese);
        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode("SMTL_DT");
        type.setTypeName("SMTL_DT");
        type.setManagementUnitType(UnitType.DEANERY);
        type.setStatus(Boolean.TRUE);
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode(code);
        course.setCourseName("DT course");
        course.setCourseType(type);
        course.setDeanery(deanery);
        return course;
    }
}
