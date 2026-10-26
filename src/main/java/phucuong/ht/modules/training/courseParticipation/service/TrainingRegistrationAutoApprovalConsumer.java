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
import phucuong.ht.modules.notification.service.WorkflowDecisionNotifier.Decision;
import phucuong.ht.modules.training.TrainingDecisionNotifier;
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
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TrainingDecisionNotifier trainingDecisionNotifier;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.modules.training.courseInvitation.service.CourseInvitationAutoTrigger invitationAutoTrigger;

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

        // Chỉ khóa bật "tự duyệt hồ sơ hợp lệ" mới được hệ thống tự duyệt; còn lại chờ duyệt thủ công.
        CourseParticipation first = participations.get(0);
        if (first.getCourse() == null || !Boolean.TRUE.equals(first.getCourse().getAutoApproveEligible())) {
            return;
        }

        List<CourseParticipation> approvedParticipations = new java.util.ArrayList<>();
        for (CourseParticipation participation : participations) {
            if (!Boolean.TRUE.equals(participation.getEligible())) {
                continue;
            }
            // Hồ sơ gửi ngoài đợt hoặc liên giáo hạt luôn chờ duyệt thủ công.
            if (CourseParticipationService.requiresManualApproval(participation)) {
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
            approvedParticipations.add(participation);
        }
        int approvedCount = approvedParticipations.size();
        // Chỉ hồ sơ đủ điều kiện mới được tự duyệt; hồ sơ ngoại lệ vẫn chờ duyệt thủ công nên không có thông báo "đã duyệt".
        if (approvedCount == 0) {
            return;
        }
        // Khóa bật "tự gửi email thông tin tham dự": gửi cho hồ sơ vừa được tự duyệt (sau khi giao dịch commit).
        if (invitationAutoTrigger != null) {
            invitationAutoTrigger.afterApproved(approvedParticipations);
        }

        int pendingCount = (int) participations.stream()
                .filter(participation -> participation.getApprovalStatus() == WorkflowApprovalStatus.PENDING)
                .count();

        UnitType recipientScopeType = UnitType.valueOf(String.valueOf(message.getPayload().get("recipientScopeType")));
        UUID recipientScopeId = UUID.fromString(String.valueOf(message.getPayload().get("recipientScopeId")));
        String recipientPermission = String.valueOf(message.getPayload().get("recipientPermission"));
        List<String> recipients = notificationRecipientResolver.resolveRecipients(null, recipientPermission, recipientScopeType, recipientScopeId);
        String courseName = approvedParticipations.get(0).getCourse() == null ? null : approvedParticipations.get(0).getCourse().getCourseName();
        String courseCode = String.valueOf(message.getPayload().get("courseCode"));
        String courseLabel = courseName == null || courseName.isBlank() ? courseCode : courseName;
        int totalCount = readInteger(message.getPayload(), "totalCount");
        String summary = String.valueOf(message.getPayload().get("senderUnitName"))
                + " có " + approvedCount + "/" + totalCount + " hồ sơ đăng ký khóa " + courseLabel + " được hệ thống tự động duyệt";
        if (pendingCount > 0) {
            summary += "; còn " + pendingCount + " hồ sơ (ngoại lệ, ngoài đợt hoặc liên giáo hạt) chờ duyệt thủ công";
        }
        trainingNotificationService.notifyUsers(recipients, TrainingNotificationCommand.builder()
                .eventType("training.registration.auto_approved")
                .title("Tự động duyệt danh sách đăng ký")
                .message(summary + ".")
                .senderUnitType(UnitType.valueOf(String.valueOf(message.getPayload().get("senderUnitType"))))
                .senderUnitId(UUID.fromString(String.valueOf(message.getPayload().get("senderUnitId"))))
                .courseCode(courseCode)
                .totalCount(totalCount)
                .approvedCount(approvedCount)
                .pendingCount(pendingCount)
                .actor(CourseParticipationService.autoApprovalActor())
                .build());

        // Người gửi đăng ký và admin giáo xứ của hồ sơ nhận kết quả duyệt (từ 3 hồ sơ cùng khóa thì gom thành một thông báo).
        if (trainingDecisionNotifier != null) {
            trainingDecisionNotifier.notifyRegistrations(Decision.REGISTRATION_AUTO_APPROVED, approvedParticipations, null,
                    CourseParticipationService.autoApprovalActor());
        }
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
