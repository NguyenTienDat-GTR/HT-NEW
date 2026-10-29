package phucuong.ht.modules.certificate.config.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CertificateConfigUpsertRequest {

    // Tên cấu hình; trùng tên trong cùng phạm vi sẽ được thêm hậu tố số: "Tên (1)", "Tên (2)"...
    private String name;

    // Khổ giấy (A3/A4/A5/LETTER/LEGAL) và hướng in (PORTRAIT/LANDSCAPE).
    private String paperSize;
    private String orientation;

    // Id ảnh nền đã tải lên qua POST /api/media (category CERTIFICATE_BACKGROUND). Null = không có ảnh nền.
    private UUID backgroundAssetId;

    // true = xóa ảnh nền kể cả ảnh base64 cũ chưa được chuyển lên cloud.
    private Boolean clearBackground;

    private List<CertificateLayoutItem> fieldLayout;
}
