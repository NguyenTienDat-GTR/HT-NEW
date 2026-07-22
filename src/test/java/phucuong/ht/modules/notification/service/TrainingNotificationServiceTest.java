package phucuong.ht.modules.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.notification.model.entity.TrainingNotification;
import phucuong.ht.modules.notification.repository.TrainingNotificationRepository;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingNotificationServiceTest {

    @Mock
    private TrainingNotificationRepository trainingNotificationRepository;

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    @Mock
    private Authentication authentication;

    @Test
    void notifyUsersShouldPersistAndPushRealtimeMessage() {
        TrainingNotificationService trainingNotificationService = new TrainingNotificationService(
                trainingNotificationRepository,
                simpMessagingTemplate,
                new securityUtils(),
                new CommonValidationHelper(new securityUtils())
        );

        when(trainingNotificationRepository.save(any())).thenAnswer(invocation -> {
            TrainingNotification notification = invocation.getArgument(0);
            notification.setId("notification-1");
            return notification;
        });

        var responses = trainingNotificationService.notifyUsers(List.of("receiver"), TrainingNotificationCommand.builder()
                .eventType("training.registration.submitted")
                .title("Danh sach dang ky moi")
                .message("Parish A gui 2 dang ky cho khoa COURSE")
                .senderUnitType(UnitType.PARISH)
                .courseCode("COURSE")
                .totalCount(2)
                .approvedCount(0)
                .pendingCount(2)
                .actor("actor")
                .build());

        assertEquals(1, responses.size());
        ArgumentCaptor<TrainingNotification> captor = ArgumentCaptor.forClass(TrainingNotification.class);
        verify(trainingNotificationRepository).save(captor.capture());
        assertEquals("receiver", captor.getValue().getUsername());
        verify(simpMessagingTemplate).convertAndSendToUser("receiver", "/queue/notifications", responses.get(0));
    }

    @Test
    void findCurrentUserNotificationsShouldPageAndIgnoreExpiredNotifications() {
        TrainingNotificationService trainingNotificationService = new TrainingNotificationService(
                trainingNotificationRepository,
                simpMessagingTemplate,
                new securityUtils(),
                new CommonValidationHelper(new securityUtils())
        );
        BaseSearchRequest request = new BaseSearchRequest();
        request.setPage(1);
        request.setSize(20);
        TrainingNotification notification = new TrainingNotification();
        notification.setId("notification-1");
        notification.setUsername("receiver");
        notification.setEventType("UNIT_STATUS_TOGGLED");
        notification.setTitle("Đơn vị của bạn đã bị tạm ngưng");
        notification.setMessage("Giáo xứ Phú Cường của bạn đã bị tạm ngưng");
        notification.setCreatedAt(OffsetDateTime.now());
        notification.setStatus(Boolean.TRUE);

        when(authentication.getName()).thenReturn("receiver");
        when(trainingNotificationRepository.findAllByUsernameAndStatusTrueAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                eq("receiver"),
                any(OffsetDateTime.class),
                eq(PageRequest.of(1, 20))
        )).thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(1, 20), 21));

        var page = trainingNotificationService.findCurrentUserNotifications(request, authentication);

        assertEquals(1, page.getContent().size());
        assertEquals(21, page.getTotalElements());
        verify(trainingNotificationRepository).deleteByCreatedAtBefore(any(OffsetDateTime.class));
    }
}
