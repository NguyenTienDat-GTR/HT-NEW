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
import phucuong.ht.modules.certificate.config.model.request.CertificateConfigAssignRequest;
import phucuong.ht.modules.certificate.config.model.request.CertificateConfigUpsertRequest;
import phucuong.ht.modules.certificate.config.model.response.CertificateConfigAssignmentResponse;
import phucuong.ht.modules.certificate.config.model.request.CertificateExportPdfRequest;
import phucuong.ht.modules.certificate.config.model.response.CertificateConfigResponse;
import phucuong.ht.modules.certificate.config.model.response.CertificateFieldCatalogItem;
import phucuong.ht.modules.certificate.config.model.response.CertifiedCourseTypeResponse;
import phucuong.ht.modules.certificate.config.model.response.CertificatePdfJobResponse;
import phucuong.ht.modules.certificate.config.model.response.CertificatePreviewResponse;
import phucuong.ht.modules.certificate.config.service.CertificateConfigService;
import phucuong.ht.modules.certificate.config.service.CertificatePdfJobService;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import org.springframework.data.domain.Page;
import phucuong.ht.common.repository.BaseSearchRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateConfigController {

    private final CertificateConfigService service;
    private final CertificatePdfJobService pdfJobService;

    @GetMapping("/config")
    @PreAuthorize("@certificateConfigAccessEvaluator.canRead(authentication)")
    public apiResponse<Page<CertificateConfigResponse>> list(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy thư viện cấu hình chứng nhận thành công", service.search(request, authentication));
    }

    // Toàn bộ cấu hình xem/dùng được ở dạng gọn (không phân trang), cho ô chọn cấu hình.
    @GetMapping("/config/options")
    @PreAuthorize("@certificateConfigAccessEvaluator.canRead(authentication)")
    public apiResponse<List<CertificateConfigResponse>> options(Authentication authentication) {
        return apiResponse.success("Lấy danh sách cấu hình chứng nhận thành công", service.options(authentication));
    }

    @GetMapping("/config/{id}")
    @PreAuthorize("@certificateConfigAccessEvaluator.canRead(authentication)")
    public apiResponse<CertificateConfigResponse> detail(@PathVariable UUID id, Authentication authentication) {
        return apiResponse.success("Lấy chi tiết cấu hình chứng nhận thành công", service.get(id, authentication));
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
    public apiResponse<CertificateConfigResponse> resolve(@RequestParam(required = false) String courseTypeCode,
                                                          Authentication authentication) {
        return apiResponse.success("Lấy cấu hình chứng nhận thành công", service.getResolved(courseTypeCode, authentication));
    }

    // Loại khóa nào đang được gán rõ ràng cấu hình nào trong phạm vi của người xem.
    @GetMapping("/config/assignments")
    @PreAuthorize("@certificateConfigAccessEvaluator.canRead(authentication)")
    public apiResponse<List<CertificateConfigAssignmentResponse>> assignments(Authentication authentication) {
        return apiResponse.success("Lấy danh sách gán cấu hình chứng nhận thành công", service.assignments(authentication));
    }

    @PutMapping("/config/assignments")
    @PreAuthorize("@certificateConfigAccessEvaluator.canUpdate(authentication)")
    public apiResponse<Void> assign(@Valid @RequestBody CertificateConfigAssignRequest request, Authentication authentication) {
        service.assign(request, authentication);
        return apiResponse.<Void>success("Gán cấu hình chứng nhận cho loại khóa thành công", null);
    }

    @PostMapping("/config")
    @PreAuthorize("@certificateConfigAccessEvaluator.canCreate(authentication)")
    public apiResponse<CertificateConfigResponse> create(@Valid @RequestBody CertificateConfigUpsertRequest request,
                                                         Authentication authentication) {
        return apiResponse.success("Tạo cấu hình chứng nhận thành công", service.create(request, authentication));
    }

    @PutMapping("/config/{id}")
    @PreAuthorize("@certificateConfigAccessEvaluator.canUpdate(authentication)")
    public apiResponse<CertificateConfigResponse> update(@PathVariable UUID id,
                                                         @Valid @RequestBody CertificateConfigUpsertRequest request,
                                                         Authentication authentication) {
        return apiResponse.success("Lưu cấu hình chứng nhận thành công", service.update(id, request, authentication));
    }

    @PutMapping("/config/{id}/default")
    @PreAuthorize("@certificateConfigAccessEvaluator.canUpdate(authentication)")
    public apiResponse<CertificateConfigResponse> setDefault(@PathVariable UUID id, Authentication authentication) {
        return apiResponse.success("Đã đặt làm cấu hình chung", service.setDefault(id, authentication));
    }

    @DeleteMapping("/config/{id}")
    @PreAuthorize("@certificateConfigAccessEvaluator.canDelete(authentication)")
    public apiResponse<Void> delete(@PathVariable UUID id, Authentication authentication) {
        service.delete(id, authentication);
        return apiResponse.<Void>success("Xóa cấu hình chứng nhận thành công", null);
    }

    // Xem trước chứng nhận với cấu hình đang áp dụng và dữ liệu thật; quyền như xem chi tiết chứng nhận.
    @GetMapping("/{certificateCode}/preview")
    @PreAuthorize("@certificateAccessEvaluator.canRead(authentication, #certificateCode)")
    public apiResponse<CertificatePreviewResponse> preview(@PathVariable String certificateCode) {
        return apiResponse.success("Lấy bản xem trước chứng nhận thành công", service.preview(certificateCode));
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
