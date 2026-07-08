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
import phucuong.ht.modules.training.enums.CourseType;
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

    @Test
    void handleShouldApproveDeaneryStatusForDtBatchSubmission() {
        TrainingRegistrationAutoApprovalConsumer consumer = new TrainingRegistrationAutoApprovalConsumer(
                courseParticipationRepository,
                new TrainingWorkflowSupport(),
                trainingNotificationRecipientResolver,
                trainingNotificationService
        );

        CourseParticipation participation = participation(CourseType.SMKN_DT);
        participation.setDeaneryApprovalStatus(WorkflowApprovalStatus.PENDING);
        when(courseParticipationRepository.findAllByIdsWithLeaderAndCourse(List.of("cp-1"))).thenReturn(List.of(participation));
        when(trainingNotificationRecipientResolver.resolveRecipients(any(), any(), any(), any())).thenReturn(List.of("approver"));

        consumer.handle(message("training.registration.batch_submitted", CourseType.SMKN_DT, "cp-1"));

        assertEquals(WorkflowApprovalStatus.APPROVED, participation.getDeaneryApprovalStatus());
        assertEquals(CourseParticipationService.autoApprovalActor(), participation.getApprovedBy());
        verify(trainingNotificationService).notifyUsers(any(), any());
    }

    @Test
    void handleShouldApproveDioceseStatusForDeanerySubmission() {
        TrainingRegistrationAutoApprovalConsumer consumer = new TrainingRegistrationAutoApprovalConsumer(
                courseParticipationRepository,
                new TrainingWorkflowSupport(),
                trainingNotificationRecipientResolver,
                trainingNotificationService
        );

        CourseParticipation participation = participation(CourseType.SMTL_I);
        participation.setDioceseApprovalStatus(WorkflowApprovalStatus.PENDING);
        when(courseParticipationRepository.findAllByIdsWithLeaderAndCourse(List.of("cp-1"))).thenReturn(List.of(participation));
        when(trainingNotificationRecipientResolver.resolveRecipients(any(), any(), any(), any())).thenReturn(List.of("approver"));

        consumer.handle(message("training.registration.deanery_batch_submitted", CourseType.SMTL_I, "cp-1"));

        assertEquals(WorkflowApprovalStatus.APPROVED, participation.getDioceseApprovalStatus());
        verify(trainingNotificationService).notifyUsers(any(), any());
    }

    @Test
    void handleShouldIgnoreParishSubmissionForNonDtCourse() {
        TrainingRegistrationAutoApprovalConsumer consumer = new TrainingRegistrationAutoApprovalConsumer(
                courseParticipationRepository,
                new TrainingWorkflowSupport(),
                trainingNotificationRecipientResolver,
                trainingNotificationService
        );

        consumer.handle(message("training.registration.batch_submitted", CourseType.SMTL_I, "cp-1"));

        verify(courseParticipationRepository, never()).findAllByIdsWithLeaderAndCourse(any());
        verify(trainingNotificationService, never()).notifyUsers(any(), any());
    }

    private DomainEventMessage message(String eventType, CourseType courseType, String participationId) {
        UUID scopeId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        return DomainEventMessage.builder()
                .eventType(eventType)
                .entityType("trainingCourse")
                .entityId("COURSE")
                .actor("actor")
                .occurredAt(OffsetDateTime.now())
                .payload(Map.of(
                        "courseCode", "COURSE",
                        "courseType", courseType.name(),
                        "participationIds", List.of(participationId),
                        "senderUnitType", "PARISH",
                        "senderUnitId", senderId.toString(),
                        "senderUnitName", "Parish A",
                        "recipientScopeType", "DEANERY",
                        "recipientScopeId", scopeId.toString(),
                        "recipientPermission", "training.participation.approve.deanery",
                        "totalCount", 1
                ))
                .build();
    }

    private CourseParticipation participation(CourseType courseType) {
        CourseParticipation participation = new CourseParticipation();
        participation.setId("cp-1");
        participation.setEligible(Boolean.TRUE);
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode("COURSE");
        course.setCourseType(courseType);
        participation.setCourse(course);
        return participation;
    }
}
