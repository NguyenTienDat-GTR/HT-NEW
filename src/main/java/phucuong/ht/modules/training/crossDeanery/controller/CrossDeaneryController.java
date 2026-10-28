package phucuong.ht.modules.training.crossDeanery.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.AccessDecisionRequest;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.Candidate;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.CourseInvitation;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.CreateRequest;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.DecisionRequest;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.OpenCourse;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryModels.RequestResponse;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryStatus;
import phucuong.ht.modules.training.crossDeanery.service.CrossDeaneryAccessService;
import phucuong.ht.modules.training.crossDeanery.service.CrossDeaneryService;

import java.util.List;

// Gửi hồ sơ liên giáo hạt: giáo xứ / giáo hạt nhà gửi, giáo hạt nhà (quyền cross_approve) đồng ý.
@RestController
@RequestMapping("/api/training/cross-deanery")
@RequiredArgsConstructor
public class CrossDeaneryController {

    private final CrossDeaneryService crossDeaneryService;
    private final CrossDeaneryAccessService crossDeaneryAccessService;

    // Khóa của giáo hạt khác đang mở liên giáo hạt: giáo hạt nhà xem thông tin khóa và quyết định cho giáo xứ gửi hay không.
    @GetMapping("/course-invitations")
    @PreAuthorize("@courseParticipationAccessEvaluator.canApproveCrossDeanery(authentication)")
    public apiResponse<List<CourseInvitation>> courseInvitations(Authentication authentication) {
        return apiResponse.success("Lấy danh sách khóa mở liên giáo hạt thành công", crossDeaneryAccessService.list(authentication));
    }

    @PostMapping("/course-invitations/{courseCode}/decision")
    @PreAuthorize("@courseParticipationAccessEvaluator.canApproveCrossDeanery(authentication)")
    public apiResponse<CourseInvitation> decideInvitation(@PathVariable String courseCode, @Valid @RequestBody AccessDecisionRequest request,
                                                          Authentication authentication) {
        return apiResponse.success(request.allowed() ? "Đã cho phép giáo xứ gửi hồ sơ sang khóa" : "Đã từ chối cho giáo xứ gửi hồ sơ sang khóa",
                crossDeaneryAccessService.decide(courseCode, request, authentication));
    }

    @GetMapping("/courses")
    @PreAuthorize("@courseParticipationAccessEvaluator.canSubmitCrossDeanery(authentication)")
    public apiResponse<List<OpenCourse>> openCourses(Authentication authentication) {
        return apiResponse.success("Lấy danh sách khóa của giáo hạt khác thành công", crossDeaneryService.openCourses(authentication));
    }

    @GetMapping("/courses/{courseCode}/candidates")
    @PreAuthorize("@courseParticipationAccessEvaluator.canSubmitCrossDeanery(authentication)")
    public apiResponse<List<Candidate>> candidates(@PathVariable String courseCode, Authentication authentication) {
        return apiResponse.success("Lấy danh sách huynh trưởng có thể gửi thành công", crossDeaneryService.candidates(courseCode, authentication));
    }

    @PostMapping("/courses/{courseCode}/requests")
    @PreAuthorize("@courseParticipationAccessEvaluator.canSubmitCrossDeanery(authentication)")
    public apiResponse<RequestResponse> create(@PathVariable String courseCode, @Valid @RequestBody CreateRequest request, Authentication authentication) {
        return apiResponse.success("Đã gửi yêu cầu liên giáo hạt", crossDeaneryService.create(courseCode, request, authentication));
    }

    @GetMapping("/requests")
    @PreAuthorize("@courseParticipationAccessEvaluator.canReadCrossDeanery(authentication)")
    public apiResponse<List<RequestResponse>> list(@RequestParam(required = false) CrossDeaneryStatus status, Authentication authentication) {
        return apiResponse.success("Lấy danh sách yêu cầu liên giáo hạt thành công", crossDeaneryService.list(status, authentication));
    }

    @GetMapping("/requests/{id}")
    @PreAuthorize("@courseParticipationAccessEvaluator.canReadCrossDeanery(authentication)")
    public apiResponse<RequestResponse> get(@PathVariable String id, Authentication authentication) {
        return apiResponse.success("Lấy yêu cầu liên giáo hạt thành công", crossDeaneryService.get(id, authentication));
    }

    @PostMapping("/requests/{id}/decision")
    @PreAuthorize("@courseParticipationAccessEvaluator.canApproveCrossDeanery(authentication)")
    public apiResponse<RequestResponse> decide(@PathVariable String id, @Valid @RequestBody DecisionRequest request, Authentication authentication) {
        return apiResponse.success(request.approved() ? "Đã đồng ý yêu cầu liên giáo hạt" : "Đã từ chối yêu cầu liên giáo hạt",
                crossDeaneryService.decide(id, request, authentication));
    }

    @DeleteMapping("/requests/{id}")
    @PreAuthorize("@courseParticipationAccessEvaluator.canSubmitCrossDeanery(authentication)")
    public apiResponse<RequestResponse> cancel(@PathVariable String id, Authentication authentication) {
        return apiResponse.success("Đã thu hồi yêu cầu liên giáo hạt", crossDeaneryService.cancel(id, authentication));
    }
}
