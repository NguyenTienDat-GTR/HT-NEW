package phucuong.ht.modules.certificate.config.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class CertificateConfigResponse {

    private UUID id;
    private String name;
    private Boolean isDefault;
    // Cấp sở hữu: SYSTEM | DIOCESE | DEANERY.
    private String scopeLevel;
    // Người đang xem có sửa/xóa được cấu hình này không (cấu hình kế thừa từ cấp trên chỉ được dùng).
    private Boolean editable;
    private String paperSize;
    private String orientation;
    // URL ảnh nền đã ký (hết hạn sau một thời gian) và id asset để gửi lại khi lưu.
    private String backgroundImage;
    private java.util.UUID backgroundAssetId;
    private List<CertificateLayoutItem> fieldLayout;
    private Boolean status;
    private OffsetDateTime updatedAt;
    // Các loại khóa đang dùng cấu hình này (tính theo phạm vi của người xem, gồm cả loại khóa dùng do là cấu hình chung).
    private List<CertifiedCourseTypeResponse> usedByCourseTypes;
    private Integer usedByCount;
}
