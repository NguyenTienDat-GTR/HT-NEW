package phucuong.ht.modules.certificate.config.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;
import phucuong.ht.modules.certificate.model.enums.CertificateSignerType;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CertificateConfigResponse {

    private UUID id;
    private String courseTypeCode;
    private String courseTypeName;
    private CertificateSignerType signerType;
    private String signerName;
    private String signerTitle;
    private String backgroundImage;
    private List<CertificateLayoutItem> fieldLayout;
    private Boolean status;
}
