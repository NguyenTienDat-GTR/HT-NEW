package phucuong.ht.modules.certificate.config.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Một cấu hình chứng nhận trong thư viện. Thuộc một phạm vi (scopeKey), có tên duy nhất trong phạm vi;
// mỗi phạm vi có đúng một cấu hình chung (isDefault). Việc dùng cho loại khóa nào nằm ở CertificateConfigAssignment.
@Entity
@Table(name = "certificate_config")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class CertificateConfig extends BaseModel {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    // Cấu hình chung của phạm vi: dùng cho loại khóa chưa được gán cấu hình riêng; không được xóa.
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;

    // Phạm vi sở hữu: SYSTEM | DIOCESE:<uuid> | DEANERY:<uuid>.
    @Column(name = "scope_key", nullable = false)
    private String scopeKey;

    // Khổ giấy (A3/A4/A5/LETTER/LEGAL) và hướng in (PORTRAIT/LANDSCAPE).
    @Column(name = "paper_size")
    private String paperSize;

    @Column(name = "orientation")
    private String orientation;

    // Ảnh nền hiện tại nằm ở media_asset (cloud); cột base64 cũ chỉ để job chuyển dữ liệu đọc.
    @Column(name = "background_asset_id")
    private UUID backgroundAssetId;

    @Column(name = "background_image", columnDefinition = "text")
    private String legacyBackgroundData;

    // Danh sách phần tử (trường cố định + văn bản tĩnh) đặt trên ảnh nền, lưu JSONB.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_layout", columnDefinition = "jsonb")
    private List<CertificateLayoutItem> fieldLayout = new ArrayList<>();
}
