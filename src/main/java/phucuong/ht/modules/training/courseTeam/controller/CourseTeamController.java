package phucuong.ht.modules.training.courseTeam.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.training.courseTeam.model.request.TeamAssignRequest;
import phucuong.ht.modules.training.courseTeam.model.request.TeamDivideRequest;
import phucuong.ht.modules.training.courseTeam.model.response.TeamOption;
import phucuong.ht.modules.training.courseTeam.model.response.TeamOverviewResponse;
import phucuong.ht.modules.training.courseTeam.service.CourseTeamService;

import java.util.List;

@RestController
@RequestMapping("/api/training/courses/{courseCode}/teams")
@RequiredArgsConstructor
public class CourseTeamController {

    private final CourseTeamService courseTeamService;

    @GetMapping("/overview")
    @PreAuthorize("@courseParticipationAccessEvaluator.canReadCourseTeams(authentication, #courseCode)")
    public apiResponse<TeamOverviewResponse> overview(@PathVariable String courseCode) {
        return apiResponse.success("Lấy thông tin chia đội thành công", courseTeamService.overview(courseCode));
    }

    // Danh sách tên đội hiện có của khóa (dùng cho bộ lọc theo đội).
    @GetMapping("/options")
    @PreAuthorize("@courseParticipationAccessEvaluator.canReadCourseTeams(authentication, #courseCode)")
    public apiResponse<List<TeamOption>> options(@PathVariable String courseCode) {
        return apiResponse.success("Lấy danh sách đội thành công", courseTeamService.options(courseCode));
    }

    @PostMapping("/divide")
    @PreAuthorize("@courseParticipationAccessEvaluator.canManageCourseTeams(authentication, #courseCode)")
    public apiResponse<TeamOverviewResponse> divide(@PathVariable String courseCode, @Valid @RequestBody TeamDivideRequest request, Authentication authentication) {
        return apiResponse.success("Chia đội thành công", courseTeamService.divide(courseCode, request, authentication));
    }

    @PatchMapping("/assignments")
    @PreAuthorize("@courseParticipationAccessEvaluator.canManageCourseTeams(authentication, #courseCode)")
    public apiResponse<TeamOverviewResponse> assign(@PathVariable String courseCode, @Valid @RequestBody TeamAssignRequest request, Authentication authentication) {
        return apiResponse.success("Đã lưu đội cho hồ sơ", courseTeamService.assign(courseCode, request, authentication));
    }

    @DeleteMapping
    @PreAuthorize("@courseParticipationAccessEvaluator.canManageCourseTeams(authentication, #courseCode)")
    public apiResponse<TeamOverviewResponse> clear(@PathVariable String courseCode, Authentication authentication) {
        return apiResponse.success("Đã xóa chia đội của khóa", courseTeamService.clear(courseCode, authentication));
    }
}
