package phucuong.ht.modules.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.notification.model.entity.TrainingNotification;
import phucuong.ht.modules.notification.model.response.TrainingNotificationResponse;
import phucuong.ht.modules.notification.model.response.UnreadNotificationCountResponse;
import phucuong.ht.modules.notification.repository.TrainingNotificationRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TrainingNotificationService {

    private static final int RETENTION_DAYS = 30;

    private final TrainingNotificationRepository trainingNotificationRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final securityUtils securityUtils;
    private final CommonValidationHelper commonValidationHelper;

    @Transactional
    public List<TrainingNotificationResponse> notifyUsers(List<String> usernames, TrainingNotificationCommand command) {
        return notifyUsers(usernames, command, true);
    }

    private List<TrainingNotificationResponse> notifyUsers(List<String> usernames, TrainingNotificationCommand command, boolean realtime) {
        if (usernames == null || usernames.isEmpty()) {
            return List.of();
        }

        purgeExpiredNotifications();
        Set<String> uniqueUsernames = normalizeUsernames(usernames);
        List<TrainingNotificationResponse> responses = new ArrayList<>();
        List<Delivery> deliveries = new ArrayList<>();
        for (String username : uniqueUsernames) {
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
            notification.setPayload(command.payload());
            notification.setCreatedAt(now);
            notification.setCreatedBy(command.actor());
            notification.setUpdatedAt(now);
            notification.setUpdatedBy(command.actor());
            notification.setStatus(Boolean.TRUE);
            TrainingNotification saved = trainingNotificationRepository.save(notification);
            TrainingNotificationResponse response = toResponse(saved);
            responses.add(response);
            deliveries.add(new Delivery(username, response));
        }

        if (realtime) {
            publishAfterCommit(deliveries);
        }
        return responses;
    }

    @Transactional
    public Page<TrainingNotificationResponse> findCurrentUserNotifications(BaseSearchRequest request, Authentication authentication) {
        purgeExpiredNotifications();
        String username = currentUsername(authentication);
        BaseSearchRequest criteria = request == null ? new BaseSearchRequest() : request;
        return trainingNotificationRepository.findAllByUsernameAndStatusTrueAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        username,
                        retentionCutoff(),
                        PageRequest.of(criteria.getPage(), criteria.getSize())
                )
                .map(this::toResponse);
    }

    @Transactional
    public TrainingNotificationResponse markRead(String id, Authentication authentication) {
        purgeExpiredNotifications();
        String username = currentUsername(authentication);
        TrainingNotification notification = trainingNotificationRepository.findByIdAndUsernameAndStatusTrueAndCreatedAtGreaterThanEqual(id, username, retentionCutoff())
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy thông báo " + id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now());
            notification.setUpdatedAt(notification.getReadAt());
            notification.setUpdatedBy(username);
        }
        return toResponse(notification);
    }

    @Transactional
    public UnreadNotificationCountResponse unreadCount(Authentication authentication) {
        purgeExpiredNotifications();
        return UnreadNotificationCountResponse.builder()
                .unreadCount(trainingNotificationRepository.countByUsernameAndStatusTrueAndReadAtIsNullAndCreatedAtGreaterThanEqual(currentUsername(authentication), retentionCutoff()))
                .build();
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
                .payload(notification.getPayload())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .status(notification.getStatus())
                .build();
    }

    private Set<String> normalizeUsernames(List<String> usernames) {
        Set<String> normalized = new LinkedHashSet<>();
        if (usernames == null) {
            return normalized;
        }
        for (String username : usernames) {
            if (username != null && !username.isBlank()) {
                normalized.add(username);
            }
        }
        return normalized;
    }

    private void publishAfterCommit(List<Delivery> deliveries) {
        if (deliveries.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliveries.forEach(TrainingNotificationService.this::sendNow);
                }
            });
            return;
        }
        deliveries.forEach(this::sendNow);
    }

    private void sendNow(Delivery delivery) {
        simpMessagingTemplate.convertAndSendToUser(delivery.username(), "/queue/notifications", delivery.response());
    }

    private OffsetDateTime retentionCutoff() {
        return OffsetDateTime.now().minusDays(RETENTION_DAYS);
    }

    private void purgeExpiredNotifications() {
        trainingNotificationRepository.deleteByCreatedAtBefore(retentionCutoff());
    }

    private record Delivery(String username, TrainingNotificationResponse response) {
    }
}
