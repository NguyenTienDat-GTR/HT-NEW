package phucuong.ht.modules.training.trainingCourse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.config.DomainEventMessage;
import phucuong.ht.config.rabbitMQConfig;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AuthPermissionQueryRepository;
import phucuong.ht.modules.notification.service.TrainingNotificationCommand;
import phucuong.ht.modules.notification.service.TrainingNotificationService;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Theo mô hình event-driven: khi khóa mở/đóng đăng ký, thông báo cho các tài khoản có quyền gửi hoặc duyệt
// danh sách đăng ký trong phạm vi khóa. Thông báo được lưu DB rồi đẩy realtime qua STOMP tới các tài khoản đang online
// ngay khi có sự kiện từ RabbitMQ (không thăm dò); tài khoản offline đọc lại qua REST khi đăng nhập.
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseRegistrationEventConsumer {

    private static final List<String> PARTICIPANT_PERMISSIONS = List.of(
            CourseParticipationAccessEvaluator.SUBMIT_PARISH,
            CourseParticipationAccessEvaluator.SUBMIT_DEANERY,
            CourseParticipationAccessEvaluator.APPROVE_DEANERY,
            CourseParticipationAccessEvaluator.APPROVE_DIOCESE
    );

    private final AuthPermissionQueryRepository authPermissionQueryRepository;
    private final AccountRepository accountRepository;
    private final TrainingNotificationService trainingNotificationService;

    @RabbitListener(queues = rabbitMQConfig.DOMAIN_COURSE_REGISTRATION_QUEUE)
    @Transactional
    public void handle(DomainEventMessage message) {
        if (message == null || message.getPayload() == null) {
            return;
        }
        boolean opened = TrainingCourseService.EVENT_REGISTRATION_OPENED.equals(message.getEventType());
        if (!opened && !TrainingCourseService.EVENT_REGISTRATION_CLOSED.equals(message.getEventType())) {
            return;
        }
        Map<String, Object> payload = message.getPayload();
        boolean deaneryManaged = Boolean.parseBoolean(String.valueOf(payload.get("deaneryManaged")));
        UUID scopeId = readUuid(payload.get(deaneryManaged ? "deaneryId" : "dioceseId"));
        if (scopeId == null) {
            log.warn("Bỏ qua sự kiện {} thiếu phạm vi đơn vị: {}", message.getEventType(), message.getEntityId());
            return;
        }

        List<String> recipients = resolveRecipients(deaneryManaged, scopeId);
        String courseCode = String.valueOf(payload.get("courseCode"));
        String courseName = String.valueOf(payload.get("courseName"));
        boolean auto = "AUTO".equals(String.valueOf(payload.get("reason")));
        trainingNotificationService.notifyUsers(recipients, TrainingNotificationCommand.builder()
                .eventType(message.getEventType())
                .title(opened ? "Khóa huấn luyện đã mở đăng ký" : "Khóa huấn luyện đã đóng đăng ký")
                .message(buildMessage(courseName, opened, auto, message.getActor()))
                .senderUnitType(deaneryManaged ? UnitType.DEANERY : UnitType.DIOCESE)
                .senderUnitId(scopeId)
                .courseCode(courseCode)
                .actor(message.getActor())
                .build());
    }

    private String buildMessage(String courseName, boolean opened, boolean auto, String actor) {
        if (auto) {
            return opened
                    ? "Khóa " + courseName + " đã được hệ thống tự động mở đăng ký do đến thời gian đăng ký."
                    : "Khóa " + courseName + " đã được hệ thống tự động đóng đăng ký do hết thời gian đăng ký.";
        }
        String by = actor == null || actor.isBlank() ? "quản trị viên" : actor;
        return "Khóa " + courseName + " đã được " + by + (opened ? " mở lại đăng ký." : " đóng đăng ký.");
    }

    // Tài khoản có quyền gửi hoặc duyệt danh sách đăng ký, thuộc giáo hạt (khóa giáo hạt) hoặc giáo phận (khóa giáo phận) của khóa.
    private List<String> resolveRecipients(boolean deaneryManaged, UUID scopeId) {
        Set<String> candidates = new LinkedHashSet<>();
        for (String permission : PARTICIPANT_PERMISSIONS) {
            candidates.addAll(authPermissionQueryRepository.findUsernamesByPermissionCode(permission));
        }
        return candidates.stream()
                .filter(username -> username != null && !username.isBlank())
                .filter(username -> (deaneryManaged
                        ? accountRepository.findActiveDeaneryIdByUsername(username)
                        : accountRepository.findActiveDioceseIdByUsername(username))
                        .map(scopeId::equals)
                        .orElse(false))
                .toList();
    }

    private UUID readUuid(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank() || "null".equals(String.valueOf(raw))) {
            return null;
        }
        return UUID.fromString(String.valueOf(raw));
    }
}
