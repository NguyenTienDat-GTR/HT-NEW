package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import phucuong.ht.modules.system.repository.SystemLogRepository;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemLogRetentionServiceTest {

    @Mock
    private SystemLogRepository systemLogRepository;

    @Test
    void purgeExpiredLogsShouldDeleteLogsOlderThanTenDays() {
        SystemLogRetentionService service = new SystemLogRetentionService(systemLogRepository);
        OffsetDateTime now = OffsetDateTime.parse("2026-07-08T10:15:30+07:00");
        OffsetDateTime cutoff = now.minusDays(SystemLogRetentionService.RETENTION_DAYS);

        when(systemLogRepository.deleteByLoggedAtBefore(eq(cutoff))).thenReturn(7);

        int deleted = service.purgeExpiredLogs(now);

        assertEquals(7, deleted);
        verify(systemLogRepository).deleteByLoggedAtBefore(cutoff);
    }
}
