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
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver;
import phucuong.ht.modules.notification.service.TrainingNotificationService;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipationService;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryItemStatus;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.CreateRequest;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.DecisionRequest;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.ItemInput;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.RequestResponse;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryStatus;
import phucuong.ht.modules.training.crossDeanery.model.entity.CrossDeaneryRequest;
import phucuong.ht.modules.training.crossDeanery.model.entity.CrossDeaneryRequestItem;
import phucuong.ht.modules.training.crossDeanery.repository.CrossDeaneryRequestRepository;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrossDeaneryServiceTest {

    private CrossDeaneryRequestRepository requestRepository;
    private CourseParticipationRepository participationRepository;
    private CourseParticipationService participationService;
    private LeaderRepository leaderRepository;
    private TrainingNotificationService notificationService;
    private securityUtils security;
    private AccountScopeValidationHelper scope;
    private CrossDeaneryService service;
    private final Authentication authentication = mock(Authentication.class);
    private phucuong.ht.modules.auth.repository.AccountRepository accountRepository;

    private final Diocese diocese = diocese();
    private final Deanery courseDeanery = deanery("Giáo hạt A", diocese);
    private final Deanery homeDeanery = deanery("Giáo hạt B", diocese);
    private final Parish parish = parish("Giáo xứ C", homeDeanery);
    private TrainingCourse course;
    private Leader leader;
    private final List<CourseParticipation> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        requestRepository = mock(CrossDeaneryRequestRepository.class);
        TrainingCourseRepository courseRepository = mock(TrainingCourseRepository.class);
        participationRepository = mock(CourseParticipationRepository.class);
        participationService = mock(CourseParticipationService.class);
        leaderRepository = mock(LeaderRepository.class);
        ParishRepository parishRepository = mock(ParishRepository.class);
        DeaneryRepository deaneryRepository = mock(DeaneryRepository.class);
        CommonValidationHelper validation = mock(CommonValidationHelper.class);
        scope = mock(AccountScopeValidationHelper.class);
        security = mock(securityUtils.class);
        notificationService = mock(TrainingNotificationService.class);
        TrainingNotificationRecipientResolver resolver = mock(TrainingNotificationRecipientResolver.class);
        AuditActorNameResolver names = mock(AuditActorNameResolver.class);
        accountRepository = mock(phucuong.ht.modules.auth.repository.AccountRepository.class);

        when(validation.validationError(anyString())).thenAnswer(inv -> new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, inv.getArgument(0)));
        when(validation.forbidden(anyString())).thenAnswer(inv -> new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, inv.getArgument(0)));
        when(validation.conflict(anyString())).thenAnswer(inv -> new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, inv.getArgument(0)));
        when(validation.notFound(anyString())).thenAnswer(inv -> new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, inv.getArgument(0)));
        when(validation.requireAuditActor(any(), anyString())).thenReturn("parish-user");
        when(security.hasAuthority(any(), eq(CourseParticipationAccessEvaluator.SUBMIT_PARISH))).thenReturn(true);
        when(scope.requireActiveParishId(any(), anyString())).thenReturn(parish.getId());
        when(parishRepository.findByIdWithDeaneryAndDiocese(parish.getId())).thenReturn(Optional.of(parish));
        when(parishRepository.findById(parish.getId())).thenReturn(Optional.of(parish));
        when(deaneryRepository.findById(homeDeanery.getId())).thenReturn(Optional.of(homeDeanery));

        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode("DT");
        type.setManagementUnitType(UnitType.DEANERY);
        course = new TrainingCourse();
        course.setCourseCode("KH-A");
        course.setCourseName("Dự trưởng A");
        course.setCourseType(type);
        course.setDeanery(courseDeanery);
        course.setStatus(Boolean.TRUE);
        course.setRegistrationEndAt(OffsetDateTime.now().plusDays(3));
        when(courseRepository.findByCodeWithType("KH-A")).thenReturn(Optional.of(course));

        leader = new Leader();
        leader.setId(UUID.randomUUID());
        leader.setFirstName("An");
        leader.setLastName("Nguyễn");
        leader.setParish(parish);
        leader.setStatus(Boolean.TRUE);
        when(leaderRepository.findByIdWithParish(leader.getId())).thenReturn(Optional.of(leader));
        when(participationService.isEligible(any(), any(), any(), any())).thenReturn(true);
        when(participationService.newPendingParticipation(any(), any(), anyBoolean(), any(), any(), any())).thenAnswer(inv -> {
            CourseParticipation participation = new CourseParticipation();
            participation.setId("p-" + saved.size());
            participation.setLeader(inv.getArgument(0));
            participation.setCourse(inv.getArgument(1));
            participation.setEligible(inv.getArgument(2));
            participation.setExceptionReason(inv.getArgument(3));
            participation.setCreatedBy(inv.getArgument(4));
            participation.setApprovalStatus(WorkflowApprovalStatus.PENDING);
            return participation;
        });
        when(participationRepository.save(any())).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        service = new CrossDeaneryService(requestRepository, courseRepository, participationRepository, participationService, leaderRepository,
                parishRepository, deaneryRepository, new TrainingWorkflowSupport(), mock(CourseActivityGuard.class), validation, scope, security,
                mock(LeaderLevelService.class), resolver, notificationService, names, accountRepository);
    }

    private CreateRequest request(String reason) {
        return new CreateRequest(List.of(new ItemInput(leader.getId(), reason, null, "ghi chú")));
    }

    private CrossDeaneryRequest pendingRequest() {
        CrossDeaneryRequest entity = new CrossDeaneryRequest();
        entity.setId("req-1");
        entity.setCourse(course);
        entity.setHomeDeaneryId(homeDeanery.getId());
        entity.setParishId(parish.getId());
        entity.setRequesterType(UnitType.PARISH);
        entity.setRequestStatus(CrossDeaneryStatus.PENDING);
        entity.setCreatedBy("parish-user");
        CrossDeaneryRequestItem item = new CrossDeaneryRequestItem();
        item.setId("item-1");
        item.setRequest(entity);
        item.setLeader(leader);
        item.setReason("Giáo hạt A thiếu người");
        item.setNote("ghi chú");
        item.setItemStatus(CrossDeaneryItemStatus.PENDING);
        entity.getItems().add(item);
        return entity;
    }

    private void asApprover() {
        when(security.hasAuthority(any(), eq(CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY))).thenReturn(true);
        when(scope.requireActiveDeaneryId(any(), eq(CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY))).thenReturn(homeDeanery.getId());
    }

    @Test
    void parishRequestStaysPendingAndCreatesNoParticipation() {
        RequestResponse response = service.create("KH-A", request("Giáo hạt A thiếu người"), authentication);

        assertEquals(CrossDeaneryStatus.PENDING, response.requestStatus());
        assertEquals(1, response.items().size());
        assertEquals(CrossDeaneryItemStatus.PENDING, response.items().get(0).itemStatus());
        assertTrue(saved.isEmpty());
        verify(notificationService).notifyUsers(anyList(), any());
        verify(participationService, never()).announceSubmission(any(), anyList(), any(), any(), any(), any());
    }

    @Test
    void reasonIsRequiredForEveryLeader() {
        assertThrows(ApiException.class, () -> service.create("KH-A", request("  "), authentication));
        assertTrue(saved.isEmpty());
    }

    @Test
    void cannotSendToOwnDeaneryCourse() {
        course.setDeanery(homeDeanery);
        assertThrows(ApiException.class, () -> service.create("KH-A", request("lý do"), authentication));
    }

    @Test
    void cannotSendLeaderWhoAlreadyHasParticipation() {
        when(participationRepository.existsByLeaderIdAndCourseCode(leader.getId(), "KH-A")).thenReturn(true);
        ApiException error = assertThrows(ApiException.class, () -> service.create("KH-A", request("lý do"), authentication));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void decisionNotificationGoesToUsernameOfLeaderLinkedRequester() {
        asApprover();
        CrossDeaneryRequest request = pendingRequest();
        UUID requesterLeaderId = UUID.randomUUID();
        request.setCreatedBy(requesterLeaderId.toString());
        when(requestRepository.findByIdWithDetails("req-1")).thenReturn(Optional.of(request));
        when(accountRepository.findActiveUsernamesByLeaderId(requesterLeaderId)).thenReturn(List.of("parish-admin"));

        service.decide("req-1", new DecisionRequest(true, null, List.of()), authentication);

        verify(notificationService).notifyUsers(eq(List.of("parish-admin")), any());
    }

    @Test
    void approvalCreatesPendingParticipationMarkedCrossDeanery() {
        asApprover();
        when(requestRepository.findByIdWithDetails("req-1")).thenReturn(Optional.of(pendingRequest()));

        RequestResponse response = service.decide("req-1", new DecisionRequest(true, null, List.of()), authentication);

        assertEquals(CrossDeaneryStatus.APPROVED, response.requestStatus());
        assertEquals(1, saved.size());
        CourseParticipation participation = saved.get(0);
        assertEquals("Giáo hạt A thiếu người", participation.getCrossDeaneryReason());
        assertEquals(homeDeanery.getId(), participation.getHomeDeaneryId());
        assertEquals("ghi chú", participation.getRegistrationNote());
        assertEquals(WorkflowApprovalStatus.PENDING, participation.getApprovalStatus());
        assertEquals("parish-user", participation.getCreatedBy());
        assertNotNull(participation.getHomeDeaneryApprovedAt());
        assertTrue(CourseParticipationService.requiresManualApproval(participation));
        verify(participationService).announceSubmission(eq(course), anyList(), eq(UnitType.PARISH), eq(parish.getId()), eq("Giáo xứ C"), anyString());
    }

    @Test
    void rejectionNeedsNoteAndCreatesNothing() {
        asApprover();
        when(requestRepository.findByIdWithDetails("req-1")).thenReturn(Optional.of(pendingRequest()));
        assertThrows(ApiException.class, () -> service.decide("req-1", new DecisionRequest(false, " ", null), authentication));

        RequestResponse response = service.decide("req-1", new DecisionRequest(false, "Không đủ chỗ", null), authentication);
        assertEquals(CrossDeaneryStatus.REJECTED, response.requestStatus());
        assertTrue(saved.isEmpty());
    }

    @Test
    void onlyHomeDeaneryApproverCanDecide() {
        asApprover();
        when(scope.requireActiveDeaneryId(any(), eq(CourseParticipationAccessEvaluator.CROSS_APPROVE_DEANERY))).thenReturn(UUID.randomUUID());
        when(requestRepository.findByIdWithDetails("req-1")).thenReturn(Optional.of(pendingRequest()));
        ApiException error = assertThrows(ApiException.class, () -> service.decide("req-1", new DecisionRequest(true, null, null), authentication));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    @Test
    void partialApprovalRequiresNoteForRejectedItems() {
        asApprover();
        CrossDeaneryRequest request = pendingRequest();
        Leader second = new Leader();
        second.setId(UUID.randomUUID());
        second.setFirstName("Bình");
        second.setParish(parish);
        CrossDeaneryRequestItem other = new CrossDeaneryRequestItem();
        other.setId("item-2");
        other.setRequest(request);
        other.setLeader(second);
        other.setReason("lý do 2");
        other.setItemStatus(CrossDeaneryItemStatus.PENDING);
        request.getItems().add(other);
        when(requestRepository.findByIdWithDetails("req-1")).thenReturn(Optional.of(request));

        assertThrows(ApiException.class, () -> service.decide("req-1", new DecisionRequest(true, null, List.of("item-1")), authentication));
        RequestResponse response = service.decide("req-1", new DecisionRequest(true, "Hết chỗ", List.of("item-1")), authentication);

        assertEquals(1, saved.size());
        assertEquals(CrossDeaneryItemStatus.APPROVED, response.items().get(0).itemStatus());
        assertEquals(CrossDeaneryItemStatus.REJECTED, response.items().get(1).itemStatus());
    }

    @Test
    void decidedRequestCannotBeDecidedAgain() {
        asApprover();
        CrossDeaneryRequest request = pendingRequest();
        request.setRequestStatus(CrossDeaneryStatus.APPROVED);
        when(requestRepository.findByIdWithDetails("req-1")).thenReturn(Optional.of(request));
        ApiException error = assertThrows(ApiException.class, () -> service.decide("req-1", new DecisionRequest(true, null, null), authentication));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void parishCanCancelOwnPendingRequest() {
        when(requestRepository.findByIdWithDetails("req-1")).thenReturn(Optional.of(pendingRequest()));
        RequestResponse response = service.cancel("req-1", authentication);
        assertEquals(CrossDeaneryStatus.CANCELLED, response.requestStatus());
        assertEquals(CrossDeaneryItemStatus.CANCELLED, response.items().get(0).itemStatus());
        assertNull(response.decisionNote());
    }

    @Test
    void expiredRequestIsReportedAsExpired() {
        course.setRegistrationEndAt(OffsetDateTime.now().minusDays(1));
        CrossDeaneryRequest request = pendingRequest();
        assertEquals(CrossDeaneryStatus.EXPIRED, CrossDeaneryService.effectiveStatus(request));
    }

    private static Diocese diocese() {
        Diocese diocese = new Diocese();
        diocese.setId(UUID.randomUUID());
        diocese.setName("Giáo phận");
        return diocese;
    }

    private static Deanery deanery(String name, Diocese diocese) {
        Deanery deanery = new Deanery();
        deanery.setId(UUID.randomUUID());
        deanery.setName(name);
        deanery.setDiocese(diocese);
        return deanery;
    }

    private static Parish parish(String name, Deanery deanery) {
        Parish parish = new Parish();
        parish.setId(UUID.randomUUID());
        parish.setName(name);
        parish.setDeanery(deanery);
        return parish;
    }
}
