package phucuong.ht.modules.media.job;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Các tác vụ nền của module media. Mỗi tác vụ bật/tắt riêng bằng app.media.*.enabled. */
public final class MediaJobs {

    private MediaJobs() {
    }

    @Component
    @RequiredArgsConstructor
    @ConditionalOnProperty(prefix = "app.media.legacy-migration", name = "enabled", havingValue = "true")
    public static class LegacyMigrationJob {
        private static final Logger log = LoggerFactory.getLogger(LegacyMigrationJob.class);
        private final MediaLegacyMigrationService service;

        @Scheduled(
                fixedDelayString = "${app.media.legacy-migration.fixed-delay-ms:30000}",
                initialDelayString = "${app.media.legacy-migration.initial-delay-ms:20000}")
        public void run() {
            try {
                service.migrateBatch();
            } catch (RuntimeException e) {
                log.error("Job chuyển ảnh base64 cũ lỗi", e);
            }
        }
    }

    @Component
    @RequiredArgsConstructor
    @ConditionalOnProperty(prefix = "app.media.provider-migration", name = "enabled", havingValue = "true")
    public static class ProviderMigrationJob {
        private static final Logger log = LoggerFactory.getLogger(ProviderMigrationJob.class);
        private final MediaProviderMigrationService service;

        @Scheduled(
                fixedDelayString = "${app.media.provider-migration.fixed-delay-ms:30000}",
                initialDelayString = "${app.media.provider-migration.initial-delay-ms:30000}")
        public void run() {
            try {
                service.migrateBatch();
            } catch (RuntimeException e) {
                log.error("Job chuyển nhà cung cấp lưu trữ lỗi", e);
            }
        }
    }

    @Component
    @RequiredArgsConstructor
    @ConditionalOnProperty(prefix = "app.media.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
    public static class MaintenanceJob {
        private static final Logger log = LoggerFactory.getLogger(MaintenanceJob.class);
        private final MediaMaintenanceService service;

        @Scheduled(
                fixedDelayString = "${app.media.cleanup.fixed-delay-ms:3600000}",
                initialDelayString = "${app.media.cleanup.initial-delay-ms:120000}")
        public void run() {
            try {
                service.cleanup();
                service.relocateLeaderAvatars();
            } catch (RuntimeException e) {
                log.error("Job dọn dẹp ảnh lỗi", e);
            }
        }
    }
}
