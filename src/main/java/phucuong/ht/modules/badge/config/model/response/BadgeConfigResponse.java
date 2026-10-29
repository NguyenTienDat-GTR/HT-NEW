package phucuong.ht.modules.badge.config.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class BadgeConfigResponse {

    private UUID id;
    private String name;
    private Boolean isDefault;
    // Cấp sở hữu: SYSTEM | DIOCESE | DEANERY.
    private String scopeLevel;
    // Người đang xem có sửa / xóa được cấu hình này không (cấu hình của cấp trên chỉ được dùng).
    private Boolean editable;
    private String paperSize;
    private String paperOrientation;
    private Double cardWidthMm;
    private Double cardHeightMm;
    private String cardOrientation;
    private Double marginMm;
    private Double gapMm;
    private Boolean cropMarks;
    private Boolean hasBack;
    // Số thẻ xếp được trên một trang giấy theo khổ thẻ đã chọn (cột x hàng).
    private Integer columns;
    private Integer rows;
    private Integer cardsPerPage;
    // Chỉ có ở chi tiết, không có ở danh sách gọn.
    // URL ảnh nền đã ký (hết hạn sau một thời gian) và id asset để gửi lại khi lưu.
    private String frontBackground;
    private String backBackground;
    private java.util.UUID frontBackgroundAssetId;
    private java.util.UUID backBackgroundAssetId;
    private List<CertificateLayoutItem> frontLayout;
    private List<CertificateLayoutItem> backLayout;
    private Boolean status;
    private OffsetDateTime updatedAt;
}
