package phucuong.ht.modules.certificate.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CertificateCreateRequest {

    @NotBlank
    private String certificateCode;

    private String oldCertificateCode;

    @NotBlank
    private String certificateName;

    private String leaderCode;

    private String courseCode;

    private String result;

    private String signedBy;

    private LocalDate issueDate;

    private String imageUrl;
}
