package phucuong.ht.modules.training.courseAttendance.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.ScanRequest;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.ScanResult;
import phucuong.ht.modules.training.courseAttendance.service.CourseAttendanceService;
import phucuong.ht.modules.training.courseParticipation.security.CourseParticipationAccessEvaluator;

// Quét mã QR điểm danh ở header: khóa huấn luyện nằm trong chính mã QR nên không cần chọn khóa.
// Quyền quét được kiểm tra theo khóa lấy từ mã (đã xác thực chữ ký), giống endpoint theo khóa.
@RestController
@RequestMapping("/api/training/attendance")
@RequiredArgsConstructor
public class AttendanceScanController {

    private final CourseAttendanceService attendanceService;
    private final CourseParticipationAccessEvaluator accessEvaluator;
    private final CommonValidationHelper commonValidationHelper;

    @PostMapping("/lookup")
    @PreAuthorize("isAuthenticated()")
    public apiResponse<ScanResult> lookup(@Valid @RequestBody ScanRequest request, Authentication authentication) {
        requireCanScan(request.token(), authentication);
        return apiResponse.success("Tra cứu mã điểm danh thành công", attendanceService.lookup(request.token()));
    }

    @PostMapping("/scan")
    @PreAuthorize("isAuthenticated()")
    public apiResponse<ScanResult> scan(@Valid @RequestBody ScanRequest request, Authentication authentication) {
        requireCanScan(request.token(), authentication);
        return apiResponse.success("Quét mã điểm danh thành công", attendanceService.scan(request.token(), authentication));
    }

    private void requireCanScan(String token, Authentication authentication) {
        String courseCode = attendanceService.courseCodeOf(token);
        if (!accessEvaluator.canScanAttendance(authentication, courseCode)) {
            throw commonValidationHelper.forbidden("Bạn không có quyền điểm danh cho khóa huấn luyện này");
        }
    }
}
