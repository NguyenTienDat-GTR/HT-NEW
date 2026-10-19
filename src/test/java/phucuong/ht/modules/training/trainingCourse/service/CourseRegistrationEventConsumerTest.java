package phucuong.ht.modules.training.trainingCourse.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import phucuong.ht.config.DomainEventMessage;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AuthPermissionQueryRepository;
import phucuong.ht.modules.notification.service.TrainingNotificationCommand;
import phucuong.ht.modules.notification.service.TrainingNotificationService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseRegistrationEventConsumerTest {

    @Mock private AuthPermissionQueryRepository authPermissionQueryRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private TrainingNotificationService trainingNotificationService;

    @Test
    void notifiesOnlyScopedSubmittersAndApproversWithoutSocketPush() {
        UUID deaneryId = UUID.randomUUID();
        when(authPermissionQueryRepository.findUsernamesByPermissionCode(anyString())).thenReturn(List.of());
        when(authPermissionQueryRepository.findUsernamesByPermissionCode("training.participation.submit.parish")).thenReturn(List.of("parish-in", "parish-out"));
        when(authPermissionQueryRepository.findUsernamesByPermissionCode("training.participation.approve.deanery")).thenReturn(List.of("deanery-in", "parish-in"));
        when(accountRepository.findActiveDeaneryIdByUsername("parish-in")).thenReturn(Optional.of(deaneryId));
        when(accountRepository.findActiveDeaneryIdByUsername("deanery-in")).thenReturn(Optional.of(deaneryId));
        when(accountRepository.findActiveDeaneryIdByUsername("parish-out")).thenReturn(Optional.of(UUID.randomUUID()));

        DomainEventMessage message = DomainEventMessage.builder()
                .eventType(TrainingCourseService.EVENT_REGISTRATION_CLOSED)
                .entityId("C1")
                .actor("admin")
                .payload(Map.of("courseCode", "C1", "courseName", "Khóa 1", "open", false, "reason", "MANUAL",
                        "deaneryManaged", true, "deaneryId", deaneryId.toString()))
                .build();

        new CourseRegistrationEventConsumer(authPermissionQueryRepository, accountRepository, trainingNotificationService).handle(message);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> recipients = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<TrainingNotificationCommand> command = ArgumentCaptor.forClass(TrainingNotificationCommand.class);
        verify(trainingNotificationService).storeForUsers(recipients.capture(), command.capture());
        verify(trainingNotificationService, never()).notifyUsers(any(), any());
        assertEquals(List.of("parish-in", "deanery-in"), recipients.getValue());
        assertEquals("C1", command.getValue().courseCode());
        assertEquals(TrainingCourseService.EVENT_REGISTRATION_CLOSED, command.getValue().eventType());
    }

    @Test
    void ignoresUnrelatedEvents() {
        DomainEventMessage message = DomainEventMessage.builder().eventType("training.score.updated").payload(Map.of()).build();
        new CourseRegistrationEventConsumer(authPermissionQueryRepository, accountRepository, trainingNotificationService).handle(message);
        verify(trainingNotificationService, never()).storeForUsers(any(), any());
    }
}
