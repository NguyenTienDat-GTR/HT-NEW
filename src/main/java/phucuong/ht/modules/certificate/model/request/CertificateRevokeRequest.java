package phucuong.ht.modules.certificate.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CertificateRevokeRequest {
    // Lý do thu hồi, bắt buộc.
    private String reason;
}
