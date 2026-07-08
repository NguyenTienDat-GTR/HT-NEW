package phucuong.ht.modules.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.notification.model.entity.TrainingNotification;
import phucuong.ht.modules.notification.repository.TrainingNotificationRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingNotificationServiceTest {

    @Mock
    private TrainingNotificationRepository trainingNotificationRepository;

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

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
}
