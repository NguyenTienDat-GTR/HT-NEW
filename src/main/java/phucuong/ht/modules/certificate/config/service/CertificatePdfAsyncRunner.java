package phucuong.ht.modules.certificate.config.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

// Chạy nền việc sinh PDF (tách khỏi JobService để @Async hoạt động qua proxy, không bị self-invocation).
@Component
@RequiredArgsConstructor
public class CertificatePdfAsyncRunner {

    private static final Logger log = LoggerFactory.getLogger(CertificatePdfAsyncRunner.class);

    private final CertificateConfigService certificateConfigService;

    @Async("certificatePdfExecutor")
    public void run(CertificatePdfJobService.Job job, List<String> certificateCodes) {
        try {
            CertificateConfigService.PdfExport export = certificateConfigService.exportPdf(certificateCodes);
            job.markDone(export.content(), export.filename());
        } catch (Exception e) {
            job.markFailed(e.getMessage() == null ? "Không thể tạo PDF chứng nhận" : e.getMessage());
            log.warn("Tạo PDF chứng nhận thất bại (job {}): {}", job.getId(), e.toString());
        }
    }
}
