package phucuong.ht.modules.training.courseParticipation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingApprovalRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingRegistrationRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingScoreRequest;
import phucuong.ht.modules.training.courseParticipation.model.response.CourseParticipationResponse;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipationService;

import java.util.List;

@RestController
@RequestMapping("/api/training")
@RequiredArgsConstructor
public class CourseParticipationController {

    private final CourseParticipationService courseParticipationService;

    @PostMapping("/courses/{courseCode}/registrations")
    @PreAuthorize("@courseParticipationAccessEvaluator.canRegister(authentication, #courseCode)")
    public apiResponse<CourseParticipationResponse> register(
            @PathVariable String courseCode,
            @Valid @RequestBody TrainingRegistrationRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Gửi đăng ký khóa huấn luyện thành công", courseParticipationService.register(courseCode, request, authentication));
    }

    @PostMapping("/courses/{courseCode}/deanery-submit")
    @PreAuthorize("@courseParticipationAccessEvaluator.canDeanerySubmit(authentication, #courseCode)")
    public apiResponse<List<CourseParticipationResponse>> deanerySubmit(
            @PathVariable String courseCode,
            Authentication authentication
    ) {
        return apiResponse.success("Tổng hợp danh sách lên giáo phận thành công", courseParticipationService.deanerySubmit(courseCode, authentication));
    }

    @PostMapping("/registrations/{id}/deanery-approval")
    @PreAuthorize("@courseParticipationAccessEvaluator.canDeaneryApprove(authentication, #id)")
    public apiResponse<CourseParticipationResponse> deaneryApprove(
            @PathVariable String id,
            @Valid @RequestBody TrainingApprovalRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Duyệt đăng ký cấp giáo hạt thành công", courseParticipationService.deaneryApprove(id, request, authentication));
    }

    @PostMapping("/registrations/{id}/diocese-approval")
    @PreAuthorize("@courseParticipationAccessEvaluator.canDioceseApprove(authentication, #id)")
    public apiResponse<CourseParticipationResponse> dioceseApprove(
            @PathVariable String id,
            @Valid @RequestBody TrainingApprovalRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Duyệt đăng ký cấp giáo phận thành công", courseParticipationService.dioceseApprove(id, request, authentication));
    }

    @PatchMapping("/participations/{id}/score")
    @PreAuthorize("@courseParticipationAccessEvaluator.canScore(authentication, #id)")
    public apiResponse<CourseParticipationResponse> score(
            @PathVariable String id,
            @Valid @RequestBody TrainingScoreRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật điểm thành công", courseParticipationService.score(id, request, authentication));
    }
}
