package phucuong.ht.modules.training.courseInvitation.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.training.courseInvitation.model.InvitationModels.SendRequest;

import java.util.List;

// Chạy nền việc gửi email thông tin tham dự (tách khỏi JobService để @Async hoạt động qua proxy).
@Component
@RequiredArgsConstructor
public class CourseInvitationAsyncRunner {

    private static final Logger log = LoggerFactory.getLogger(CourseInvitationAsyncRunner.class);

    private final CourseInvitationService invitationService;

    @Async("invitationMailExecutor")
    public void runJob(CourseInvitationJobService.Job job, SendRequest request) {
        try {
            job.markDone(invitationService.sendInvitations(job.getCourseCode(), request.participationIds(), request.includeQr(), request.resend()));
        } catch (Exception e) {
            job.markFailed(e.getMessage() == null ? "Không thể gửi email thông tin tham dự" : e.getMessage());
            log.warn("Gửi email thông tin tham dự thất bại (job {}): {}", job.getId(), e.toString());
        }
    }

    // Tự gửi khi hồ sơ vừa được duyệt: kèm mã QR khi khóa bật điểm danh, không gửi lại hồ sơ đã gửi.
    // Lỗi từng hồ sơ đã được ghi vào invitation_error nên người dùng thấy ở danh sách tham dự.
    @Async("invitationMailExecutor")
    public void autoSend(String courseCode, List<String> participationIds) {
        try {
            invitationService.sendInvitations(courseCode, participationIds, true, false);
        } catch (Exception e) {
            log.warn("Tự động gửi email thông tin tham dự khóa {} thất bại: {}", courseCode, e.toString());
        }
    }
}
