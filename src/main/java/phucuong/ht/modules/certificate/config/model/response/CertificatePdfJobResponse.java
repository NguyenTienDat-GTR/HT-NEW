package phucuong.ht.modules.certificate.config.model.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CertificatePdfJobResponse {
    private String jobId;
    private String status; // PENDING | DONE | FAILED
    private String filename;
    private String error;
}
