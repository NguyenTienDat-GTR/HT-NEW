package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.modules.system.repository.SystemLogRepository;

import java.time.OffsetDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class SystemLogRetentionService {

    static final String SYSTEM_ACTOR = "system:log-retention";
    static final int RETENTION_DAYS = 10;

    private final SystemLogRepository systemLogRepository;

    @Transactional
    public int purgeExpiredLogs() {
        return purgeExpiredLogs(OffsetDateTime.now());
    }

    @Transactional
    int purgeExpiredLogs(OffsetDateTime now) {
        OffsetDateTime cutoff = now.minusDays(RETENTION_DAYS);
        int deleted = systemLogRepository.deleteByLoggedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} system logs older than {} days", deleted, RETENTION_DAYS);
        }
        return deleted;
    }
}
