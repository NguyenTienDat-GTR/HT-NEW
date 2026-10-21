package phucuong.ht.modules.certificate.config.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;

import java.util.List;

@Getter
@Setter
public class CertificateConfigUpsertRequest {

    // Tên cấu hình; trùng tên trong cùng phạm vi sẽ được thêm hậu tố số: "Tên (1)", "Tên (2)"...
    private String name;

    // Khổ giấy (A3/A4/A5/LETTER/LEGAL) và hướng in (PORTRAIT/LANDSCAPE).
    private String paperSize;
    private String orientation;

    // Ảnh nền dạng data URL base64.
    private String backgroundImage;

    private List<CertificateLayoutItem> fieldLayout;
}
