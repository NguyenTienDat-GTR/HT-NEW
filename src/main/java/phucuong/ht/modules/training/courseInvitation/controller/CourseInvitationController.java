package phucuong.ht.modules.training.courseInvitation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.training.courseInvitation.model.InvitationModels.JobResponse;
import phucuong.ht.modules.training.courseInvitation.model.InvitationModels.SendRequest;
import phucuong.ht.modules.training.courseInvitation.service.CourseInvitationJobService;

@RestController
@RequestMapping("/api/training/courses/{courseCode}/invitations")
@RequiredArgsConstructor
public class CourseInvitationController {

    private final CourseInvitationJobService jobService;

    // Gửi email thông tin tham dự chạy nền: trả jobId ngay, hỏi trạng thái bằng GET /jobs/{jobId}.
    @PostMapping("/send")
    @PreAuthorize("@courseParticipationAccessEvaluator.canManageCourseParticipants(authentication, #courseCode)")
    public apiResponse<JobResponse> send(@PathVariable String courseCode, @Valid @RequestBody SendRequest request) {
        CourseInvitationJobService.Job job = jobService.submit(courseCode, request);
        return apiResponse.success("Đã nhận yêu cầu gửi email thông tin tham dự", jobService.toResponse(job));
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("@courseParticipationAccessEvaluator.canManageCourseParticipants(authentication, #courseCode)")
    public apiResponse<JobResponse> status(@PathVariable String courseCode, @PathVariable String jobId) {
        CourseInvitationJobService.Job job = jobService.get(courseCode, jobId);
        if (job == null) {
            return apiResponse.success("Không tìm thấy yêu cầu gửi (có thể đã hết hạn)",
                    new JobResponse(jobId, "FAILED", null, "Yêu cầu gửi không còn tồn tại"));
        }
        return apiResponse.success("Trạng thái gửi email thông tin tham dự", jobService.toResponse(job));
    }
}
