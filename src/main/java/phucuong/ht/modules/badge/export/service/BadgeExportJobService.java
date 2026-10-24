package phucuong.ht.modules.badge.export.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import phucuong.ht.modules.badge.export.model.BadgeExportRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Hàng đợi xuất PDF thẻ tham dự chạy nền (cả khóa có thể hàng trăm thẻ): submit trả jobId ngay, client hỏi trạng thái rồi tải file.
// Lưu trong bộ nhớ (ứng dụng một instance), tự dọn job cũ.
@Service
@RequiredArgsConstructor
public class BadgeExportJobService {

    private static final Duration JOB_TTL = Duration.ofMinutes(30);

    public enum Status { PENDING, DONE, FAILED }

    @Getter
    public static class Job {
        private final String id = UUID.randomUUID().toString();
        private final String courseCode;
        private volatile Status status = Status.PENDING;
        private volatile byte[] content;
        private volatile String filename;
        private volatile int badgeCount;
        private volatile int pageCount;
        private volatile String error;
        private final Instant createdAt = Instant.now();

        Job(String courseCode) {
            this.courseCode = courseCode;
        }

        void markDone(BadgeExportService.PdfExport export) {
            this.content = export.content();
            this.filename = export.filename();
            this.badgeCount = export.badgeCount();
            this.pageCount = export.pageCount();
            this.status = Status.DONE;
        }

        void markFailed(String error) {
            this.error = error;
            this.status = Status.FAILED;
        }
    }

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final BadgeExportAsyncRunner runner;

    public Job submit(String courseCode, BadgeExportRequest request) {
        pruneExpired();
        Job job = new Job(courseCode);
        jobs.put(job.getId(), job);
        runner.run(job, request);
        return job;
    }

    // Chỉ trả job của đúng khóa trong đường dẫn.
    public Job get(String courseCode, String jobId) {
        pruneExpired();
        Job job = jobs.get(jobId);
        return job != null && job.getCourseCode().equals(courseCode) ? job : null;
    }

    // Lấy job đã xong để tải file rồi xóa khỏi bộ nhớ.
    public Job take(String courseCode, String jobId) {
        Job job = get(courseCode, jobId);
        if (job != null && job.getStatus() == Status.DONE) {
            jobs.remove(jobId);
        }
        return job;
    }

    private void pruneExpired() {
        Instant cutoff = Instant.now().minus(JOB_TTL);
        jobs.values().removeIf(job -> job.getCreatedAt().isBefore(cutoff));
    }
}
