package phucuong.ht.modules.certificate.config.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

// Một trường cố định có thể chèn vào chứng nhận (mail merge).
@Getter
@AllArgsConstructor
public class CertificateFieldCatalogItem {
    private String key;
    private String label;
    private String group;
}
