package phucuong.ht.modules.training.courseCopy.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.Candidate;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.CopyRequest;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.CopyResult;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.SourcesResponse;
import phucuong.ht.modules.training.courseCopy.service.CourseCopyService;

import java.util.List;

// Sao chép danh sách tham dự của khóa tiên quyết đã kết thúc: người duyệt đăng ký đúng đơn vị sở hữu khóa.
@RestController
@RequestMapping("/api/training/courses/{courseCode}/prerequisite-copy")
@RequiredArgsConstructor
public class CourseCopyController {

    private final CourseCopyService courseCopyService;

    @GetMapping("/sources")
    @PreAuthorize("@courseParticipationAccessEvaluator.canManageCourseParticipants(authentication, #courseCode)")
    public apiResponse<SourcesResponse> sources(@PathVariable String courseCode) {
        return apiResponse.success("Lấy danh sách khóa tiên quyết thành công", courseCopyService.sources(courseCode));
    }

    @GetMapping("/candidates")
    @PreAuthorize("@courseParticipationAccessEvaluator.canManageCourseParticipants(authentication, #courseCode)")
    public apiResponse<List<Candidate>> candidates(@PathVariable String courseCode,
                                                   @RequestParam List<String> sourceCourseCodes,
                                                   @RequestParam(defaultValue = "false") boolean includeFailed) {
        return apiResponse.success("Lấy danh sách hồ sơ có thể sao chép thành công",
                courseCopyService.candidates(courseCode, sourceCourseCodes, includeFailed));
    }

    @PostMapping
    @PreAuthorize("@courseParticipationAccessEvaluator.canManageCourseParticipants(authentication, #courseCode)")
    public apiResponse<CopyResult> copy(@PathVariable String courseCode, @Valid @RequestBody CopyRequest request, Authentication authentication) {
        CopyResult result = courseCopyService.copy(courseCode, request, authentication);
        return apiResponse.success("Đã sao chép " + result.copied() + " hồ sơ vào danh sách tham dự", result);
    }
}
