package phucuong.ht.modules.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.notification.model.entity.TrainingNotification;
import phucuong.ht.modules.notification.model.response.TrainingNotificationResponse;
import phucuong.ht.modules.notification.repository.TrainingNotificationRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrainingNotificationService {

    private final TrainingNotificationRepository trainingNotificationRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final securityUtils securityUtils;
    private final CommonValidationHelper commonValidationHelper;

    @Transactional
    public List<TrainingNotificationResponse> notifyUsers(List<String> usernames, TrainingNotificationCommand command) {
        if (usernames == null || usernames.isEmpty()) {
            return List.of();
        }

        List<TrainingNotificationResponse> responses = new ArrayList<>();
        for (String username : usernames) {
            OffsetDateTime now = OffsetDateTime.now();
            TrainingNotification notification = new TrainingNotification();
            notification.setUsername(username);
            notification.setEventType(command.eventType());
            notification.setTitle(command.title());
            notification.setMessage(command.message());
            notification.setSenderUnitType(command.senderUnitType());
            notification.setSenderUnitId(command.senderUnitId());
            notification.setCourseCode(command.courseCode());
            notification.setTotalCount(command.totalCount());
            notification.setApprovedCount(command.approvedCount());
            notification.setPendingCount(command.pendingCount());
            notification.setCreatedAt(now);
            notification.setCreatedBy(command.actor());
            notification.setUpdatedAt(now);
            notification.setUpdatedBy(command.actor());
            notification.setStatus(Boolean.TRUE);
            TrainingNotification saved = trainingNotificationRepository.save(notification);
            TrainingNotificationResponse response = toResponse(saved);
            responses.add(response);
            simpMessagingTemplate.convertAndSendToUser(username, "/queue/notifications", response);
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public List<TrainingNotificationResponse> findCurrentUserNotifications(Authentication authentication) {
        String username = currentUsername(authentication);
        return trainingNotificationRepository.findAllByUsernameAndStatusTrueOrderByCreatedAtDesc(username).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TrainingNotificationResponse markRead(String id, Authentication authentication) {
        String username = currentUsername(authentication);
        TrainingNotification notification = trainingNotificationRepository.findByIdAndUsernameAndStatusTrue(id, username)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy thông báo " + id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now());
            notification.setUpdatedAt(notification.getReadAt());
            notification.setUpdatedBy(username);
        }
        return toResponse(notification);
    }

    private String currentUsername(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            throw commonValidationHelper.forbidden("Không xác định được tài khoản hiện tại");
        }
        return username;
    }

    private TrainingNotificationResponse toResponse(TrainingNotification notification) {
        return TrainingNotificationResponse.builder()
                .id(notification.getId())
                .eventType(notification.getEventType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .senderUnitType(notification.getSenderUnitType())
                .senderUnitId(notification.getSenderUnitId())
                .courseCode(notification.getCourseCode())
                .totalCount(notification.getTotalCount())
                .approvedCount(notification.getApprovedCount())
                .pendingCount(notification.getPendingCount())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .status(notification.getStatus())
                .build();
    }
}
