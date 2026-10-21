package phucuong.ht.modules.training.courseParticipation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.config.DomainEventMessage;
import phucuong.ht.config.EvictCaches;
import phucuong.ht.config.rabbitMQConfig;
import phucuong.ht.modules.notification.service.TrainingNotificationCommand;
import phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver;
import phucuong.ht.modules.notification.service.TrainingNotificationService;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TrainingRegistrationAutoApprovalConsumer {

    private final CourseParticipationRepository courseParticipationRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final TrainingNotificationRecipientResolver notificationRecipientResolver;
    private final TrainingNotificationService trainingNotificationService;

    @RabbitListener(queues = rabbitMQConfig.DOMAIN_WORKFLOW_QUEUE)
    @EvictCaches.Courses
    @EvictCaches.Analytics
    @Transactional
    public void handle(DomainEventMessage message) {
        if (message == null || message.getPayload() == null) {
            return;
        }
        if (!"training.registration.batch_submitted".equals(message.getEventType())) {
            return;
        }

        List<String> participationIds = readStringList(message.getPayload(), "participationIds");
        List<CourseParticipation> participations = courseParticipationRepository.findAllByIdsWithLeaderAndCourse(participationIds);
        if (participations.isEmpty()) {
            return;
        }

        int approvedCount = 0;
        for (CourseParticipation participation : participations) {
            if (!Boolean.TRUE.equals(participation.getEligible())) {
                continue;
            }
            // Khóa đã bị vô hiệu hóa thì không tự duyệt.
            if (participation.getCourse() != null && Boolean.FALSE.equals(participation.getCourse().getStatus())) {
                continue;
            }
            if (participation.getApprovalStatus() == WorkflowApprovalStatus.APPROVED
                    || participation.getApprovalStatus() == WorkflowApprovalStatus.REJECTED) {
                continue;
            }
            participation.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
            participation.setParticipationStatus(ParticipationStatus.ATTENDED);
            participation.setApprovedBy(CourseParticipationService.autoApprovalActor());
            participation.setUpdatedBy(CourseParticipationService.autoApprovalActor());
            approvedCount++;
        }

        int pendingCount = (int) participations.stream()
                .filter(participation -> participation.getApprovalStatus() == WorkflowApprovalStatus.PENDING)
                .count();

        UnitType recipientScopeType = UnitType.valueOf(String.valueOf(message.getPayload().get("recipientScopeType")));
        UUID recipientScopeId = UUID.fromString(String.valueOf(message.getPayload().get("recipientScopeId")));
        String recipientPermission = String.valueOf(message.getPayload().get("recipientPermission"));
        List<String> recipients = notificationRecipientResolver.resolveRecipients(null, recipientPermission, recipientScopeType, recipientScopeId);
        trainingNotificationService.notifyUsers(recipients, TrainingNotificationCommand.builder()
                .eventType("training.registration.auto_approved")
                .title("Tu dong duyet danh sach dang ky")
                .message(String.valueOf(message.getPayload().get("senderUnitName"))
                        + " da duoc he thong xu ly " + String.valueOf(message.getPayload().get("totalCount"))
                        + " dang ky cho khoa " + String.valueOf(message.getPayload().get("courseCode")))
                .senderUnitType(UnitType.valueOf(String.valueOf(message.getPayload().get("senderUnitType"))))
                .senderUnitId(UUID.fromString(String.valueOf(message.getPayload().get("senderUnitId"))))
                .courseCode(String.valueOf(message.getPayload().get("courseCode")))
                .totalCount(readInteger(message.getPayload(), "totalCount"))
                .approvedCount(approvedCount)
                .pendingCount(pendingCount)
                .actor(CourseParticipationService.autoApprovalActor())
                .build());
    }

    @SuppressWarnings("unchecked")
    private List<String> readStringList(Map<String, Object> payload, String key) {
        Object raw = payload.get(key);
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private Integer readInteger(Map<String, Object> payload, String key) {
        Object raw = payload.get(key);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(raw));
    }
}
