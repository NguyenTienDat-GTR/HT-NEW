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
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.AnySource;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.Candidate;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.CopyRequest;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.CopyResult;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.SourcesResponse;
import phucuong.ht.modules.training.courseCopy.service.CourseCopyService;

import java.util.List;

// Sao chép danh sách tham dự của khóa tiên quyết đã kết thúc: người duyệt đăng ký đúng đơn vị sở hữu khóa.
@RestController
@RequestMapping("/api/training/courses")
@RequiredArgsConstructor
public class CourseCopyController {

    private final CourseCopyService courseCopyService;

    // Khóa nguồn để sao chép danh sách tham dự ngay trong form tạo / sửa khóa. Sửa khóa: truyền courseCode (cần quyền sửa khóa đó);
    // tạo khóa: truyền courseTypeCode (cần quyền tạo loại khóa đó).
    @GetMapping("/copy-sources")
    @PreAuthorize("(#courseCode != null and @trainingCourseAccessEvaluator.canUpdate(authentication, #courseCode))"
            + " or (#courseCode == null and @trainingCourseAccessEvaluator.canCreate(authentication, #courseTypeCode))")
    public apiResponse<List<AnySource>> anySources(@RequestParam(required = false) String courseCode,
                                                   @RequestParam(required = false) String courseTypeCode,
                                                   Authentication authentication) {
        return apiResponse.success("Lấy danh sách khóa nguồn thành công", courseCopyService.anySources(courseCode, courseTypeCode, authentication));
    }

    // Hồ sơ trong các khóa nguồn đã chọn để chọn từng huynh trưởng cần sao chép (hộp thoại trong form tạo / sửa khóa).
    @GetMapping("/copy-sources/candidates")
    @PreAuthorize("(#courseCode != null and @trainingCourseAccessEvaluator.canUpdate(authentication, #courseCode))"
            + " or (#courseCode == null and @trainingCourseAccessEvaluator.canCreate(authentication, #courseTypeCode))")
    public apiResponse<List<Candidate>> anyCandidates(@RequestParam(required = false) String courseCode,
                                                      @RequestParam(required = false) String courseTypeCode,
                                                      @RequestParam List<String> sourceCourseCodes,
                                                      Authentication authentication) {
        return apiResponse.success("Lấy danh sách hồ sơ của khóa nguồn thành công",
                courseCopyService.anyCandidates(courseCode, courseTypeCode, sourceCourseCodes, authentication));
    }

    @GetMapping("/{courseCode}/prerequisite-copy/sources")
    @PreAuthorize("@courseParticipationAccessEvaluator.canManageCourseParticipants(authentication, #courseCode)")
    public apiResponse<SourcesResponse> sources(@PathVariable String courseCode) {
        return apiResponse.success("Lấy danh sách khóa tiên quyết thành công", courseCopyService.sources(courseCode));
    }

    @GetMapping("/{courseCode}/prerequisite-copy/candidates")
    @PreAuthorize("@courseParticipationAccessEvaluator.canManageCourseParticipants(authentication, #courseCode)")
    public apiResponse<List<Candidate>> candidates(@PathVariable String courseCode,
                                                   @RequestParam List<String> sourceCourseCodes,
                                                   @RequestParam(defaultValue = "false") boolean includeFailed) {
        return apiResponse.success("Lấy danh sách hồ sơ có thể sao chép thành công",
                courseCopyService.candidates(courseCode, sourceCourseCodes, includeFailed));
    }

    // Sao chép danh sách tham dự từ khóa nguồn bất kỳ (đã kết thúc / khóa quá khứ cùng phạm vi) vào khóa đã có.
    @PostMapping("/{courseCode}/participants-copy")
    @PreAuthorize("@courseParticipationAccessEvaluator.canManageCourseParticipants(authentication, #courseCode)")
    public apiResponse<CopyResult> copyToCourse(@PathVariable String courseCode, @Valid @RequestBody CourseCopyModels.CopyToCourseRequest request,
                                                Authentication authentication) {
        CopyResult result = courseCopyService.copyToCourse(courseCode, request, authentication);
        return apiResponse.success("Đã sao chép " + result.copied() + " hồ sơ vào danh sách tham dự", result);
    }

    @PostMapping("/{courseCode}/prerequisite-copy")
    @PreAuthorize("@courseParticipationAccessEvaluator.canManageCourseParticipants(authentication, #courseCode)")
    public apiResponse<CopyResult> copy(@PathVariable String courseCode, @Valid @RequestBody CopyRequest request, Authentication authentication) {
        CopyResult result = courseCopyService.copy(courseCode, request, authentication);
        return apiResponse.success("Đã sao chép " + result.copied() + " hồ sơ vào danh sách tham dự", result);
    }
}
