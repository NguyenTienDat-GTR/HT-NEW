package phucuong.ht.modules.certificate.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CertificateReissueRequest {
    // Lý do cấp lại, bắt buộc.
    private String reason;
}
