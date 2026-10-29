package phucuong.ht.modules.notification.service;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.notification.repository.TrainingNotificationRepository;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationReadRetentionServiceTest {

    @Test
    void deletesNotificationsReadMoreThanFifteenDaysAgo() {
        TrainingNotificationRepository repository = mock(TrainingNotificationRepository.class);
        OffsetDateTime now = OffsetDateTime.parse("2026-10-29T10:00:00+07:00");
        when(repository.deleteByReadAtBefore(now.minusDays(15))).thenReturn(4L);

        assertEquals(4L, new NotificationReadRetentionService(repository, 15).purgeReadNotifications(now));
        verify(repository).deleteByReadAtBefore(now.minusDays(15));
    }

    @Test
    void retentionDaysIsConfigurableAndNeverBelowOneDay() {
        TrainingNotificationRepository repository = mock(TrainingNotificationRepository.class);
        OffsetDateTime now = OffsetDateTime.parse("2026-10-29T10:00:00+07:00");
        new NotificationReadRetentionService(repository, 30).purgeReadNotifications(now);
        verify(repository).deleteByReadAtBefore(now.minusDays(30));
        new NotificationReadRetentionService(repository, 0).purgeReadNotifications(now);
        verify(repository).deleteByReadAtBefore(now.minusDays(1));
    }
}
