package phucuong.ht.modules.badge.config.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class BadgeConfigUpsertRequest {

    // Tên cấu hình; trùng tên trong cùng phạm vi sẽ được thêm hậu tố số: "Tên (1)", "Tên (2)"...
    private String name;

    // Khổ giấy in (A3/A4/A5/LETTER/LEGAL) và hướng giấy (PORTRAIT/LANDSCAPE); mặc định A4 dọc.
    private String paperSize;
    private String paperOrientation;

    // Kích thước thẻ theo mm (rộng x cao) và hướng thẻ (LANDSCAPE/PORTRAIT); mặc định 90 x 55 ngang.
    private Double cardWidthMm;
    private Double cardHeightMm;
    private String cardOrientation;

    // Lề giấy và khoảng cách giữa thẻ (mm); cropMarks: vẽ viền cắt quanh thẻ.
    private Double marginMm;
    private Double gapMm;
    private Boolean cropMarks;

    // Có mặt sau không; không có thì bỏ qua ảnh nền và bố cục mặt sau.
    private Boolean hasBack;

    // Id ảnh nền đã tải lên qua POST /api/media (category BADGE_BACKGROUND). Null = không có ảnh nền.
    private UUID frontBackgroundAssetId;
    private UUID backBackgroundAssetId;
    // true = xóa ảnh nền kể cả ảnh base64 cũ chưa được chuyển lên cloud.
    private Boolean clearFrontBackground;
    private Boolean clearBackBackground;
    private List<CertificateLayoutItem> frontLayout;
    private List<CertificateLayoutItem> backLayout;
}
