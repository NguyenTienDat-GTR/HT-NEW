package phucuong.ht.modules.training.courseParticipation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.config.DomainEventMessage;
import phucuong.ht.config.DomainEventPublisher;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.notification.service.TrainingNotificationCommand;
import phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver;
import phucuong.ht.modules.notification.service.TrainingNotificationService;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thông báo và sự kiện khi một danh sách đăng ký được gửi lên: báo người duyệt của đơn vị sở hữu khóa,
 * báo các giáo xứ khi giáo hạt gửi thay, và phát sự kiện để hệ thống tự duyệt hồ sơ đủ điều kiện.
 */
@Component
@RequiredArgsConstructor
public class RegistrationSubmissionNotifier {

    private static final String EVENT_BATCH_SUBMITTED = "training.registration.batch_submitted";

    private final TrainingNotificationRecipientResolver notificationRecipientResolver;
    private final TrainingNotificationService trainingNotificationService;
    private final DomainEventPublisher domainEventPublisher;
    private final TrainingWorkflowSupport trainingWorkflowSupport;

public void announce(TrainingCourse course, List<CourseParticipation> createdParticipations, UnitType senderType, UUID senderId,
                                   String senderName, String actor) {
        boolean deaneryManaged = trainingWorkflowSupport.isDeaneryManaged(course);
        String courseCode = course.getCourseCode();
        // Người nhận: tài khoản duyệt của đơn vị sở hữu khóa (giáo hạt với khóa giáo hạt, giáo phận với khóa giáo phận).
        UnitType recipientScopeType = deaneryManaged ? UnitType.DEANERY : UnitType.DIOCESE;
        UUID recipientScopeId = deaneryManaged ? course.getDeanery().getId() : course.getDiocese().getId();
        String recipientPermission = deaneryManaged
                ? CourseParticipationAccessEvaluator.APPROVE_DEANERY
                : CourseParticipationAccessEvaluator.APPROVE_DIOCESE;
        List<String> recipients = resolveCourseRecipients(course, deaneryManaged, recipientPermission, recipientScopeType, recipientScopeId);
        trainingNotificationService.notifyUsers(recipients, buildSubmissionNotification(
                "training.registration.submitted",
                senderName,
                senderType,
                senderId,
                courseCode,
                course.getCourseName(),
                createdParticipations.size(),
                actor
        ));

        publishBatchEvent(
                EVENT_BATCH_SUBMITTED,
                course,
                createdParticipations,
                senderType,
                senderId,
                senderName,
                actor,
                recipientScopeType,
                recipientScopeId,
                recipientPermission
        );
    }

public void notifyParishManagersOfDeanerySubmission(List<CourseParticipation> participations, String deaneryName, UUID deaneryId, TrainingCourse course, String actor) {
        Map<UUID, Long> countByParish = new LinkedHashMap<>();
        for (CourseParticipation participation : participations) {
            Parish parish = participation.getLeader().getParish();
            if (parish != null) {
                countByParish.merge(parish.getId(), 1L, Long::sum);
            }
        }
        String courseLabel = course.getCourseName() == null || course.getCourseName().isBlank() ? course.getCourseCode() : course.getCourseName();
        countByParish.forEach((parishId, count) -> {
            List<String> recipients = notificationRecipientResolver.resolveRecipients(null,
                    CourseParticipationAccessEvaluator.READ_PARISH, UnitType.PARISH, parishId);
            if (recipients.isEmpty()) {
                return;
            }
            trainingNotificationService.notifyUsers(recipients, TrainingNotificationCommand.builder()
                    .eventType("training.registration.submitted_by_deanery")
                    .title("Giáo hạt đã gửi đăng ký cho huynh trưởng của giáo xứ")
                    .message(deaneryName + " đã gửi " + count + " hồ sơ đăng ký của giáo xứ bạn cho khóa " + courseLabel)
                    .senderUnitType(UnitType.DEANERY)
                    .senderUnitId(deaneryId)
                    .courseCode(course.getCourseCode())
                    .totalCount(count.intValue())
                    .approvedCount(0)
                    .pendingCount(count.intValue())
                    .actor(actor)
                    .build());
        });
    }

private List<String> resolveCourseRecipients(TrainingCourse course, boolean useCourseList, String permissionCode, UnitType scopeType, UUID scopeId) {
        List<String> eligible = notificationRecipientResolver.resolveRecipients(null, permissionCode, scopeType, scopeId);
        if (!useCourseList || course.getRecipientUsernames().isEmpty()) {
            return eligible;
        }
        List<String> chosen = eligible.stream().filter(course.getRecipientUsernames()::contains).toList();
        return chosen.isEmpty() ? eligible : chosen;
    }

private TrainingNotificationCommand buildSubmissionNotification(
            String eventType,
            String senderUnitName,
            UnitType senderUnitType,
            UUID senderUnitId,
            String courseCode,
            String courseName,
            int totalCount,
            String actor
    ) {
        return TrainingNotificationCommand.builder()
                .eventType(eventType)
                .title("Danh sách đăng ký mới")
                .message(senderUnitName + " gửi " + totalCount + " đăng ký cho khóa " + (courseName == null || courseName.isBlank() ? courseCode : courseName))
                .senderUnitType(senderUnitType)
                .senderUnitId(senderUnitId)
                .courseCode(courseCode)
                .totalCount(totalCount)
                .approvedCount(0)
                .pendingCount(totalCount)
                .actor(actor)
                .build();
    }

private void publishBatchEvent(
            String eventType,
            TrainingCourse course,
            List<CourseParticipation> participations,
            UnitType senderUnitType,
            UUID senderUnitId,
            String senderUnitName,
            String actor,
            UnitType recipientScopeType,
            UUID recipientScopeId,
            String recipientPermission
    ) {
        publishEvent(eventType, "trainingCourse", course.getCourseCode(), actor, Map.ofEntries(
                Map.entry("courseCode", course.getCourseCode()),
                Map.entry("courseType", course.getCourseType() == null ? "" : course.getCourseType().getTypeCode()),
                Map.entry("deaneryManaged", trainingWorkflowSupport.isDeaneryManaged(course)),
                Map.entry("participationIds", participations.stream().map(CourseParticipation::getId).toList()),
                Map.entry("senderUnitType", senderUnitType.name()),
                Map.entry("senderUnitId", String.valueOf(senderUnitId)),
                Map.entry("senderUnitName", senderUnitName),
                Map.entry("recipientScopeType", recipientScopeType.name()),
                Map.entry("recipientScopeId", String.valueOf(recipientScopeId)),
                Map.entry("recipientPermission", recipientPermission),
                Map.entry("totalCount", participations.size())
        ));
    }

    private void publishEvent(String eventType, String entityType, String entityId, String actor, Map<String, Object> payload) {
        domainEventPublisher.publishAfterCommit(DomainEventMessage.of(eventType, entityType, entityId, actor, payload));
    }
}
