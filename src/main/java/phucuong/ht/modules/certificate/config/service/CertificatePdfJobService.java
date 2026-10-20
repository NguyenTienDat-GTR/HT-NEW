package phucuong.ht.modules.certificate.config.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Hàng đợi sinh PDF chứng nhận chạy nền: submit trả jobId ngay, client hỏi trạng thái rồi tải khi xong.
// Lưu trong bộ nhớ (ứng dụng một instance), tự dọn job cũ. Xuất vài trăm chứng nhận không khóa request.
@Service
@RequiredArgsConstructor
public class CertificatePdfJobService {

    private static final Duration JOB_TTL = Duration.ofMinutes(30);

    public enum Status { PENDING, DONE, FAILED }

    @Getter
    public static class Job {
        private final String id = UUID.randomUUID().toString();
        private volatile Status status = Status.PENDING;
        private volatile byte[] content;
        private volatile String filename;
        private volatile String error;
        private final Instant createdAt = Instant.now();

        void markDone(byte[] content, String filename) {
            this.content = content;
            this.filename = filename;
            this.status = Status.DONE;
        }

        void markFailed(String error) {
            this.error = error;
            this.status = Status.FAILED;
        }
    }

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final CertificatePdfAsyncRunner runner;

    public Job submit(List<String> certificateCodes) {
        pruneExpired();
        Job job = new Job();
        jobs.put(job.getId(), job);
        runner.run(job, certificateCodes);
        return job;
    }

    public Job get(String jobId) {
        pruneExpired();
        return jobs.get(jobId);
    }

    // Lấy job đã xong để tải file rồi xóa khỏi bộ nhớ.
    public Job take(String jobId) {
        Job job = jobs.get(jobId);
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
