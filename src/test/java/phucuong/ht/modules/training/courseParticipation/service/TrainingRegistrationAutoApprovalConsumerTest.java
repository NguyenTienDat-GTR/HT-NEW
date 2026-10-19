package phucuong.ht.modules.training.courseParticipation.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import phucuong.ht.config.DomainEventMessage;
import phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver;
import phucuong.ht.modules.notification.service.TrainingNotificationService;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingRegistrationAutoApprovalConsumerTest {

    @Mock
    private CourseParticipationRepository courseParticipationRepository;

    @Mock
    private TrainingNotificationRecipientResolver trainingNotificationRecipientResolver;

    @Mock
    private TrainingNotificationService trainingNotificationService;

    private TrainingRegistrationAutoApprovalConsumer consumer() {
        return new TrainingRegistrationAutoApprovalConsumer(
                courseParticipationRepository,
                new TrainingWorkflowSupport(),
                trainingNotificationRecipientResolver,
                trainingNotificationService
        );
    }

    @Test
    void handleShouldApproveDeaneryStatusForDtBatchSubmission() {
        CourseParticipation participation = participation();
        participation.setApprovalStatus(WorkflowApprovalStatus.PENDING);
        when(courseParticipationRepository.findAllByIdsWithLeaderAndCourse(List.of("cp-1"))).thenReturn(List.of(participation));
        when(trainingNotificationRecipientResolver.resolveRecipients(any(), any(), any(), any())).thenReturn(List.of("approver"));

        consumer().handle(message("training.registration.batch_submitted", true, "cp-1"));

        assertEquals(WorkflowApprovalStatus.APPROVED, participation.getApprovalStatus());
        assertEquals(CourseParticipationService.autoApprovalActor(), participation.getApprovedBy());
        verify(trainingNotificationService).notifyUsers(any(), any());
    }

    @Test
    void handleShouldApproveDioceseStatusForNonDtBatchSubmission() {
        CourseParticipation participation = participation();
        participation.setApprovalStatus(WorkflowApprovalStatus.PENDING);
        when(courseParticipationRepository.findAllByIdsWithLeaderAndCourse(List.of("cp-1"))).thenReturn(List.of(participation));
        when(trainingNotificationRecipientResolver.resolveRecipients(any(), any(), any(), any())).thenReturn(List.of("approver"));

        consumer().handle(message("training.registration.batch_submitted", false, "cp-1"));

        assertEquals(WorkflowApprovalStatus.APPROVED, participation.getApprovalStatus());
        verify(trainingNotificationService).notifyUsers(any(), any());
    }

    @Test
    void handleShouldIgnoreUnrelatedEvents() {
        consumer().handle(message("training.registration.other", false, "cp-1"));

        verify(courseParticipationRepository, never()).findAllByIdsWithLeaderAndCourse(any());
        verify(trainingNotificationService, never()).notifyUsers(any(), any());
    }

    private DomainEventMessage message(String eventType, boolean deaneryManaged, String participationId) {
        UUID scopeId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        return DomainEventMessage.builder()
                .eventType(eventType)
                .entityType("trainingCourse")
                .entityId("COURSE")
                .actor("actor")
                .occurredAt(OffsetDateTime.now())
                .payload(Map.ofEntries(
                        Map.entry("courseCode", "COURSE"),
                        Map.entry("deaneryManaged", deaneryManaged),
                        Map.entry("participationIds", List.of(participationId)),
                        Map.entry("senderUnitType", "PARISH"),
                        Map.entry("senderUnitId", senderId.toString()),
                        Map.entry("senderUnitName", "Parish A"),
                        Map.entry("recipientScopeType", "DEANERY"),
                        Map.entry("recipientScopeId", scopeId.toString()),
                        Map.entry("recipientPermission", "training.participation.approve.deanery"),
                        Map.entry("totalCount", 1)
                ))
                .build();
    }

    private CourseParticipation participation() {
        CourseParticipation participation = new CourseParticipation();
        participation.setId("cp-1");
        participation.setEligible(Boolean.TRUE);
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode("COURSE");
        participation.setCourse(course);
        return participation;
    }
}
