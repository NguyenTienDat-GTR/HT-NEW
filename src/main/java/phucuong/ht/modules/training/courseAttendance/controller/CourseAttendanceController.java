package phucuong.ht.modules.training.courseAttendance.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.InvitationFilter;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.ParticipantQuery;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.ParticipantRow;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.CheckInRequest;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.ScanRequest;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.ScanResult;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.Summary;
import phucuong.ht.modules.training.courseAttendance.service.CourseAttendanceService;

@RestController
@RequestMapping("/api/training/courses/{courseCode}/attendance")
@RequiredArgsConstructor
public class CourseAttendanceController {

    private final CourseAttendanceService attendanceService;

    @GetMapping("/summary")
    @PreAuthorize("@courseParticipationAccessEvaluator.canReadCourseParticipants(authentication, #courseCode)")
    public apiResponse<Summary> summary(@PathVariable String courseCode) {
        return apiResponse.success("Lấy tổng quan điểm danh thành công", attendanceService.summary(courseCode));
    }

    // Danh sách hồ sơ tham dự kèm tình trạng email và điểm danh (lọc / phân trang phía server).
    @GetMapping("/participants")
    @PreAuthorize("@courseParticipationAccessEvaluator.canReadCourseParticipants(authentication, #courseCode)")
    public apiResponse<Page<ParticipantRow>> participants(@PathVariable String courseCode,
                                                          @RequestParam(required = false) String search,
                                                          @RequestParam(required = false) String team,
                                                          @RequestParam(required = false) Boolean checkedIn,
                                                          @RequestParam(required = false) InvitationFilter invitation,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return apiResponse.success("Lấy danh sách điểm danh thành công",
                attendanceService.participants(courseCode, new ParticipantQuery(search, team, checkedIn, invitation, page, size)));
    }

    // Tra cứu mã QR (không điểm danh) để người dùng xác nhận.
    // Chi tiết một hồ sơ: tình trạng điểm danh, email và người tạo / duyệt / điểm danh.
    @GetMapping("/participants/{participationId}")
    @PreAuthorize("@courseParticipationAccessEvaluator.canReadCourseParticipants(authentication, #courseCode)")
    public apiResponse<phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.ParticipantDetail> participantDetail(
            @PathVariable String courseCode, @PathVariable String participationId) {
        return apiResponse.success("Lấy chi tiết hồ sơ điểm danh thành công", attendanceService.participantDetail(courseCode, participationId));
    }

    @PostMapping("/lookup")
    @PreAuthorize("@courseParticipationAccessEvaluator.canScanAttendance(authentication, #courseCode)")
    public apiResponse<ScanResult> lookup(@PathVariable String courseCode, @Valid @RequestBody ScanRequest request) {
        return apiResponse.success("Tra cứu mã điểm danh thành công", attendanceService.lookup(courseCode, request.token()));
    }

    @PostMapping("/scan")
    @PreAuthorize("@courseParticipationAccessEvaluator.canScanAttendance(authentication, #courseCode)")
    public apiResponse<ScanResult> scan(@PathVariable String courseCode, @Valid @RequestBody ScanRequest request,
                                        Authentication authentication) {
        return apiResponse.success("Quét mã điểm danh thành công", attendanceService.scan(courseCode, request.token(), authentication));
    }

    @PutMapping("/participants/{participationId}")
    @PreAuthorize("@courseParticipationAccessEvaluator.canScanAttendance(authentication, #courseCode)")
    public apiResponse<ScanResult> setCheckedIn(@PathVariable String courseCode, @PathVariable String participationId,
                                                @Valid @RequestBody CheckInRequest request, Authentication authentication) {
        return apiResponse.success("Đã cập nhật điểm danh",
                attendanceService.setCheckedIn(courseCode, participationId, request.checkedIn(), authentication));
    }
}
