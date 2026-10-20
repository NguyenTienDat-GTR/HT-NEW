package phucuong.ht.modules.certificate.config.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.certificate.config.model.request.CertificateConfigUpsertRequest;
import phucuong.ht.modules.certificate.config.model.request.CertificateExportPdfRequest;
import phucuong.ht.modules.certificate.config.model.response.CertificateConfigResponse;
import phucuong.ht.modules.certificate.config.model.response.CertificateFieldCatalogItem;
import phucuong.ht.modules.certificate.config.model.response.CertifiedCourseTypeResponse;
import phucuong.ht.modules.certificate.config.model.response.CertificatePdfJobResponse;
import phucuong.ht.modules.certificate.config.service.CertificateConfigService;
import phucuong.ht.modules.certificate.config.service.CertificatePdfJobService;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.List;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateConfigController {

    private final CertificateConfigService service;
    private final CertificatePdfJobService pdfJobService;

    @GetMapping("/config")
    @PreAuthorize("@certificateConfigAccessEvaluator.canRead(authentication)")
    public apiResponse<List<CertificateConfigResponse>> list() {
        return apiResponse.success("Lấy danh sách cấu hình chứng nhận thành công", service.list());
    }

    @GetMapping("/config/fields")
    @PreAuthorize("@certificateConfigAccessEvaluator.canRead(authentication)")
    public apiResponse<List<CertificateFieldCatalogItem>> fields() {
        return apiResponse.success("Lấy danh sách trường chứng nhận thành công", service.fieldCatalog());
    }

    @GetMapping("/config/course-types")
    @PreAuthorize("@certificateConfigAccessEvaluator.canRead(authentication)")
    public apiResponse<List<CertifiedCourseTypeResponse>> courseTypes() {
        return apiResponse.success("Lấy danh sách loại khóa có yêu cầu chứng nhận thành công", service.certifiedCourseTypes());
    }

    @GetMapping("/config/resolve")
    @PreAuthorize("@certificateConfigAccessEvaluator.canRead(authentication)")
    public apiResponse<CertificateConfigResponse> resolve(@RequestParam(required = false) String courseTypeCode) {
        return apiResponse.success("Lấy cấu hình chứng nhận thành công", service.getResolved(courseTypeCode));
    }

    @PutMapping("/config")
    @PreAuthorize("@certificateConfigAccessEvaluator.canManage(authentication)")
    public apiResponse<CertificateConfigResponse> upsert(@Valid @RequestBody CertificateConfigUpsertRequest request,
                                                         Authentication authentication) {
        return apiResponse.success("Lưu cấu hình chứng nhận thành công", service.upsert(request, authentication));
    }

    @DeleteMapping("/config")
    @PreAuthorize("@certificateConfigAccessEvaluator.canManage(authentication)")
    public apiResponse<Void> delete(@RequestParam(required = false) String courseTypeCode) {
        service.delete(courseTypeCode);
        return apiResponse.<Void>success("Xóa cấu hình chứng nhận thành công", null);
    }

    // Xuất PDF chứng nhận đồng bộ (giữ cho tương thích). Yêu cầu quyền đọc chứng nhận.
    @PostMapping("/export-pdf")
    @PreAuthorize("@certificateAccessEvaluator.canList(authentication)")
    public ResponseEntity<byte[]> exportPdf(@Valid @RequestBody CertificateExportPdfRequest request) {
        CertificateConfigService.PdfExport export = service.exportPdf(request.getCertificateCodes());
        return pdfResponse(export.content(), export.filename());
    }

    // Xuất PDF chạy nền: tạo job, trả jobId ngay (không khóa request dù vài trăm chứng nhận).
    @PostMapping("/export-pdf/jobs")
    @PreAuthorize("@certificateAccessEvaluator.canList(authentication)")
    public apiResponse<CertificatePdfJobResponse> submitExport(@Valid @RequestBody CertificateExportPdfRequest request) {
        CertificatePdfJobService.Job job = pdfJobService.submit(request.getCertificateCodes());
        return apiResponse.success("Đã nhận yêu cầu xuất PDF", toJobResponse(job));
    }

    @GetMapping("/export-pdf/jobs/{jobId}")
    @PreAuthorize("@certificateAccessEvaluator.canList(authentication)")
    public apiResponse<CertificatePdfJobResponse> exportJobStatus(@PathVariable String jobId) {
        CertificatePdfJobService.Job job = pdfJobService.get(jobId);
        if (job == null) {
            return apiResponse.success("Không tìm thấy yêu cầu xuất (có thể đã hết hạn)",
                    CertificatePdfJobResponse.builder().jobId(jobId).status("FAILED").error("Yêu cầu xuất không còn tồn tại").build());
        }
        return apiResponse.success("Trạng thái xuất PDF", toJobResponse(job));
    }

    @GetMapping("/export-pdf/jobs/{jobId}/file")
    @PreAuthorize("@certificateAccessEvaluator.canList(authentication)")
    public ResponseEntity<byte[]> downloadExportJob(@PathVariable String jobId) {
        CertificatePdfJobService.Job job = pdfJobService.take(jobId);
        if (job == null || job.getStatus() != CertificatePdfJobService.Status.DONE || job.getContent() == null) {
            return ResponseEntity.notFound().build();
        }
        return pdfResponse(job.getContent(), job.getFilename());
    }

    private CertificatePdfJobResponse toJobResponse(CertificatePdfJobService.Job job) {
        return CertificatePdfJobResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus().name())
                .filename(job.getFilename())
                .error(job.getError())
                .build();
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] content, String filename) {
        String encoded = URLEncoder.encode(filename == null ? "chung-nhan.pdf" : filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"certificate.pdf\"; filename*=UTF-8''" + encoded)
                .body(content);
    }
}
