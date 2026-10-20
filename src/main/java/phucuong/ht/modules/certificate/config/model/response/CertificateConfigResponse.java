package phucuong.ht.modules.certificate.config.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CertificateConfigResponse {

    private UUID id;
    private String courseTypeCode;
    private String courseTypeName;
    private String paperSize;
    private String orientation;
    private String backgroundImage;
    private List<CertificateLayoutItem> fieldLayout;
    private Boolean status;
}
