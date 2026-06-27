package phucuong.ht.modules.certificate.model.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CertificateUpdateRequest {

    private String oldCertificateCode;

    private String certificateName;

    private String leaderCode;

    private String courseCode;

    private String result;

    private String signedBy;

    private LocalDate issueDate;

    private String imageUrl;
}
