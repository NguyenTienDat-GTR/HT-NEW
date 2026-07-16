package phucuong.ht.modules.training.courseParticipation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingApprovalRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingRegistrationRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingScoreRequest;
import phucuong.ht.modules.training.courseParticipation.model.request.TrainingSubmissionRecipientRequest;
import phucuong.ht.modules.training.courseParticipation.model.response.CourseParticipationResponse;
import phucuong.ht.modules.training.courseParticipation.model.response.TrainingRegistrationBatchResponse;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipationService;
import phucuong.ht.common.repository.BaseSearchRequest;
import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/api/training")
@RequiredArgsConstructor
public class CourseParticipationController {

    private final CourseParticipationService courseParticipationService;

    @GetMapping("/participations")
    @PreAuthorize("@courseParticipationAccessEvaluator.canRead(authentication)")
    public apiResponse<Page<CourseParticipationResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách hồ sơ đào tạo thành công", courseParticipationService.findAll(request, authentication));
    }

    @GetMapping("/participations/{id}")
    @PreAuthorize("@courseParticipationAccessEvaluator.canReadOne(authentication, #id)")
    public apiResponse<CourseParticipationResponse> findById(@PathVariable String id) {
        return apiResponse.success("Lấy chi tiết hồ sơ đào tạo thành công", courseParticipationService.findById(id));
    }

    @PostMapping("/courses/{courseCode}/registrations")
    @PreAuthorize("@courseParticipationAccessEvaluator.canRegister(authentication, #courseCode)")
    public apiResponse<TrainingRegistrationBatchResponse> register(
            @PathVariable String courseCode,
            @Valid @RequestBody TrainingRegistrationRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Gửi danh sách đăng ký khóa huấn luyện thành công", courseParticipationService.register(courseCode, request, authentication));
    }

    @PostMapping("/courses/{courseCode}/deanery-submit")
    @PreAuthorize("@courseParticipationAccessEvaluator.canDeanerySubmit(authentication, #courseCode)")
    public apiResponse<TrainingRegistrationBatchResponse> deanerySubmit(
            @PathVariable String courseCode,
            @RequestBody(required = false) TrainingSubmissionRecipientRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Tổng hợp danh sách lên giáo phận thành công", courseParticipationService.deanerySubmit(courseCode, request, authentication));
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
