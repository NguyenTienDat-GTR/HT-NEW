package phucuong.ht.modules.training.courseInvitation.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import phucuong.ht.modules.training.courseInvitation.model.InvitationModels.JobResponse;
import phucuong.ht.modules.training.courseInvitation.model.InvitationModels.Result;
import phucuong.ht.modules.training.courseInvitation.model.InvitationModels.SendRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Hàng đợi gửi email thông tin tham dự chạy nền: submit trả jobId ngay (gửi hàng trăm thư mất vài phút), client hỏi trạng thái
// và nhận danh sách hồ sơ gửi lỗi khi xong. Lưu trong bộ nhớ (ứng dụng một instance), tự dọn job cũ.
@Service
@RequiredArgsConstructor
public class CourseInvitationJobService {

    private static final Duration JOB_TTL = Duration.ofHours(1);

    public enum Status { PENDING, DONE, FAILED }

    @Getter
    public static class Job {
        private final String id = UUID.randomUUID().toString();
        private final String courseCode;
        private volatile Status status = Status.PENDING;
        private volatile Result result;
        private volatile String error;
        private final Instant createdAt = Instant.now();

        Job(String courseCode) {
            this.courseCode = courseCode;
        }

        void markDone(Result result) {
            this.result = result;
            this.status = Status.DONE;
        }

        void markFailed(String error) {
            this.error = error;
            this.status = Status.FAILED;
        }
    }

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final CourseInvitationService invitationService;
    private final CourseInvitationAsyncRunner runner;

    public Job submit(String courseCode, SendRequest request) {
        invitationService.validateForSend(courseCode, request);
        pruneExpired();
        Job job = new Job(courseCode);
        jobs.put(job.getId(), job);
        runner.runJob(job, request);
        return job;
    }

    // Chỉ trả job của đúng khóa trong đường dẫn, để không đọc kết quả của khóa khác bằng cách đoán jobId.
    public Job get(String courseCode, String jobId) {
        pruneExpired();
        Job job = jobs.get(jobId);
        return job != null && job.getCourseCode().equals(courseCode) ? job : null;
    }

    public JobResponse toResponse(Job job) {
        return new JobResponse(job.getId(), job.getStatus().name(), job.getResult(), job.getError());
    }

    private void pruneExpired() {
        Instant cutoff = Instant.now().minus(JOB_TTL);
        jobs.values().removeIf(job -> job.getCreatedAt().isBefore(cutoff));
    }
}
