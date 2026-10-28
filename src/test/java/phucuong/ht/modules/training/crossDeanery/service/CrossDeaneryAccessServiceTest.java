package phucuong.ht.modules.training.crossDeanery.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.utils.AuditActorNameResolver;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.notification.service.TrainingNotificationCommand;
import phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver;
import phucuong.ht.modules.notification.service.TrainingNotificationService;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryAccessDecision;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.AccessDecisionRequest;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.CourseInvitation;
import phucuong.ht.modules.training.crossDeanery.model.entity.CrossDeaneryCourseAccess;
import phucuong.ht.modules.training.crossDeanery.repository.CrossDeaneryCourseAccessRepository;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrossDeaneryAccessServiceTest {

    private final Authentication authentication = mock(Authentication.class);
    private final Diocese diocese = new Diocese();
    private final Deanery hostDeanery = deanery("Giáo hạt A");
    private final Deanery homeDeanery = deanery("Giáo hạt B");
    private CrossDeaneryCourseAccessRepository accessRepository;
    private TrainingNotificationService notificationService;
    private TrainingNotificationRecipientResolver resolver;
    private DeaneryRepository deaneryRepository;
    private CrossDeaneryAccessService service;
    private TrainingCourse course;

    private Deanery deanery(String name) {
        Deanery deanery = new Deanery();
        deanery.setId(UUID.randomUUID());
        deanery.setName(name);
        deanery.setDiocese(diocese);
        deanery.setStatus(Boolean.TRUE);
        return deanery;
    }

    @BeforeEach
    void setUp() {
        diocese.setId(UUID.randomUUID());
        accessRepository = mock(CrossDeaneryCourseAccessRepository.class);
        notificationService = mock(TrainingNotificationService.class);
        resolver = mock(TrainingNotificationRecipientResolver.class);
        deaneryRepository = mock(DeaneryRepository.class);
        TrainingCourseRepository courseRepository = mock(TrainingCourseRepository.class);
        ParishRepository parishRepository = mock(ParishRepository.class);
        CommonValidationHelper validation = mock(CommonValidationHelper.class);
        AccountScopeValidationHelper scope = mock(AccountScopeValidationHelper.class);
        securityUtils security = mock(securityUtils.class);
        AuditActorNameResolver names = mock(AuditActorNameResolver.class);

        when(validation.validationError(anyString())).thenAnswer(inv -> new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, inv.getArgument(0)));
        when(validation.forbidden(anyString())).thenAnswer(inv -> new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, inv.getArgument(0)));
        when(validation.conflict(anyString())).thenAnswer(inv -> new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, inv.getArgument(0)));
        when(validation.notFound(anyString())).thenAnswer(inv -> new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, inv.getArgument(0)));
        when(validation.requireAuditActor(any(), anyString())).thenReturn("deanery-admin");
        when(security.hasAuthority(any(), eq(CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY))).thenReturn(true);
        when(scope.requireActiveDeaneryId(any(), anyString())).thenReturn(homeDeanery.getId());
        when(deaneryRepository.findByIdWithDiocese(homeDeanery.getId())).thenReturn(Optional.of(homeDeanery));
        when(resolver.resolveRecipients(any(), anyString(), any(), any())).thenReturn(List.of("someone"));

        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode("DT");
        type.setManagementUnitType(UnitType.DEANERY);
        course = new TrainingCourse();
        course.setCourseCode("KH-A");
        course.setCourseName("Dự trưởng A");
        course.setCourseType(type);
        course.setDeanery(hostDeanery);
        course.setStatus(Boolean.TRUE);
        course.setOpenToOtherDeaneries(Boolean.TRUE);
        when(courseRepository.findByCodeWithType("KH-A")).thenReturn(Optional.of(course));
        when(accessRepository.findByCourseAndDeanery("KH-A", homeDeanery.getId())).thenReturn(Optional.empty());

        service = new CrossDeaneryAccessService(accessRepository, courseRepository, deaneryRepository, parishRepository,
                mock(CrossDeaneryCourseInfoBuilder.class), new TrainingWorkflowSupport(), mock(CourseActivityGuard.class), validation, scope,
                security, names, resolver, notificationService);
    }

    @Test
    void allowRecordsDecisionAndNotifiesHostDeanery() {
        CourseInvitation result = service.decide("KH-A", new AccessDecisionRequest(true, null), authentication);

        assertEquals("ALLOWED", result.decision());
        verify(accessRepository).save(any(CrossDeaneryCourseAccess.class));
        verify(notificationService, org.mockito.Mockito.atLeastOnce()).notifyUsers(anyList(), any(TrainingNotificationCommand.class));
    }

    @Test
    void denyRequiresReason() {
        ApiException error = assertThrows(ApiException.class, () -> service.decide("KH-A", new AccessDecisionRequest(false, "  "), authentication));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verify(accessRepository, never()).save(any());
    }

    @Test
    void denyWithReasonIsStored() {
        CourseInvitation result = service.decide("KH-A", new AccessDecisionRequest(false, "Chưa có người"), authentication);

        assertEquals("DENIED", result.decision());
        assertEquals(CrossDeaneryAccessDecision.DENIED, org.mockito.Mockito.mockingDetails(accessRepository).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("save"))
                .map(call -> ((CrossDeaneryCourseAccess) call.getArgument(0)).getDecision()).findFirst().orElseThrow());
    }

    @Test
    void cannotDecideOwnCourse() {
        course.setDeanery(homeDeanery);

        ApiException error = assertThrows(ApiException.class, () -> service.decide("KH-A", new AccessDecisionRequest(true, null), authentication));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }

    @Test
    void cannotDecideCourseNotOpenToOthers() {
        course.setOpenToOtherDeaneries(Boolean.FALSE);

        ApiException error = assertThrows(ApiException.class, () -> service.decide("KH-A", new AccessDecisionRequest(true, null), authentication));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void courseOpenedNotifiesOtherDeaneriesWithoutDecision() {
        when(deaneryRepository.findActiveIdsByDioceseIdExcluding(diocese.getId(), hostDeanery.getId())).thenReturn(List.of(homeDeanery.getId()));
        when(accessRepository.findByCourse("KH-A")).thenReturn(List.of());

        service.notifyCourseOpened(course, "host-admin");

        verify(resolver).resolveRecipients(null, CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY, UnitType.DEANERY, homeDeanery.getId());
        verify(notificationService).notifyUsers(eq(List.of("someone")), any(TrainingNotificationCommand.class));
    }

    private static List<String> anyList() {
        return org.mockito.ArgumentMatchers.anyList();
    }
}
