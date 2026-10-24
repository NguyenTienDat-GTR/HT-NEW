package phucuong.ht.modules.badge.export.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.badge.export.model.BadgeExportRequest;

// Chạy nền việc sinh PDF thẻ (tách khỏi JobService để @Async hoạt động qua proxy, không bị self-invocation).
@Component
@RequiredArgsConstructor
public class BadgeExportAsyncRunner {

    private static final Logger log = LoggerFactory.getLogger(BadgeExportAsyncRunner.class);

    private final BadgeExportService exportService;

    @Async("certificatePdfExecutor")
    public void run(BadgeExportJobService.Job job, BadgeExportRequest request) {
        try {
            job.markDone(exportService.export(job.getCourseCode(), request));
        } catch (Exception e) {
            job.markFailed(e.getMessage() == null ? "Không thể tạo PDF thẻ tham dự" : e.getMessage());
            log.warn("Tạo PDF thẻ tham dự thất bại (job {}): {}", job.getId(), e.toString());
        }
    }
}
