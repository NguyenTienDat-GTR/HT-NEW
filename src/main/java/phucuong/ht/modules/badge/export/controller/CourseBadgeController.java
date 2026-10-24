package phucuong.ht.modules.badge.export.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.badge.export.model.BadgeExportJobResponse;
import phucuong.ht.modules.badge.export.model.BadgeExportRequest;
import phucuong.ht.modules.badge.export.service.BadgeExportJobService;
import phucuong.ht.modules.badge.export.service.BadgeExportService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

// Xuất thẻ tham dự trong từng khóa huấn luyện (quyền như xem hồ sơ tham dự của khóa).
@RestController
@RequestMapping("/api/training/courses/{courseCode}/badges")
@RequiredArgsConstructor
public class CourseBadgeController {

    private final BadgeExportJobService jobService;
    private final BadgeExportService exportService;

    // Các cấu hình thẻ chọn được khi xuất thẻ cho khóa này (không cần quyền quản lý thư viện cấu hình).
    @GetMapping("/configs")
    @PreAuthorize("@courseParticipationAccessEvaluator.canReadCourseParticipants(authentication, #courseCode)")
    public apiResponse<List<BadgeExportService.UsableConfig>> configs(@PathVariable String courseCode) {
        return apiResponse.success("Lấy danh sách cấu hình thẻ của khóa thành công", exportService.usableConfigs(courseCode));
    }

    // Xuất PDF chạy nền: tạo job, trả jobId ngay (không khóa request dù cả khóa vài trăm thẻ).
    @PostMapping("/export/jobs")
    @PreAuthorize("@courseParticipationAccessEvaluator.canReadCourseParticipants(authentication, #courseCode)")
    public apiResponse<BadgeExportJobResponse> submit(@PathVariable String courseCode, @Valid @RequestBody BadgeExportRequest request) {
        BadgeExportJobService.Job job = jobService.submit(courseCode, request);
        return apiResponse.success("Đã nhận yêu cầu xuất thẻ tham dự", toResponse(job));
    }

    @GetMapping("/export/jobs/{jobId}")
    @PreAuthorize("@courseParticipationAccessEvaluator.canReadCourseParticipants(authentication, #courseCode)")
    public apiResponse<BadgeExportJobResponse> status(@PathVariable String courseCode, @PathVariable String jobId) {
        BadgeExportJobService.Job job = jobService.get(courseCode, jobId);
        if (job == null) {
            return apiResponse.success("Không tìm thấy yêu cầu xuất (có thể đã hết hạn)",
                    BadgeExportJobResponse.builder().jobId(jobId).status("FAILED").error("Yêu cầu xuất không còn tồn tại").build());
        }
        return apiResponse.success("Trạng thái xuất thẻ tham dự", toResponse(job));
    }

    @GetMapping("/export/jobs/{jobId}/file")
    @PreAuthorize("@courseParticipationAccessEvaluator.canReadCourseParticipants(authentication, #courseCode)")
    public ResponseEntity<byte[]> download(@PathVariable String courseCode, @PathVariable String jobId) {
        BadgeExportJobService.Job job = jobService.take(courseCode, jobId);
        if (job == null || job.getStatus() != BadgeExportJobService.Status.DONE || job.getContent() == null) {
            return ResponseEntity.notFound().build();
        }
        String encoded = URLEncoder.encode(job.getFilename() == null ? "the-tham-du.pdf" : job.getFilename(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"badges.pdf\"; filename*=UTF-8''" + encoded)
                .body(job.getContent());
    }

    private BadgeExportJobResponse toResponse(BadgeExportJobService.Job job) {
        return BadgeExportJobResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus().name())
                .filename(job.getFilename())
                .badgeCount(job.getBadgeCount())
                .pageCount(job.getPageCount())
                .error(job.getError())
                .build();
    }
}
