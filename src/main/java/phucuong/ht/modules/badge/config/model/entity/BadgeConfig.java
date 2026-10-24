package phucuong.ht.modules.badge.config.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import phucuong.ht.common.model.BaseModel;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Một cấu hình thẻ tham dự trong thư viện. Thuộc một phạm vi (scopeKey), tên duy nhất trong phạm vi;
// mỗi phạm vi có đúng một cấu hình chung (isDefault). Thẻ có mặt trước và (tùy chọn) mặt sau, mỗi mặt có ảnh nền + bố cục riêng.
@Entity
@Table(name = "badge_config")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"frontBackground", "backBackground"})
@EqualsAndHashCode(callSuper = true)
public class BadgeConfig extends BaseModel {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;

    // Phạm vi sở hữu: SYSTEM | DIOCESE:<uuid> | DEANERY:<uuid>.
    @Column(name = "scope_key", nullable = false)
    private String scopeKey;

    // Khổ giấy in (A3/A4/A5/LETTER/LEGAL) và hướng giấy (PORTRAIT/LANDSCAPE).
    @Column(name = "paper_size")
    private String paperSize;

    @Column(name = "paper_orientation")
    private String paperOrientation;

    // Kích thước thẻ (mm) theo đúng hướng thẻ: LANDSCAPE thì rộng >= cao, PORTRAIT thì rộng <= cao.
    @Column(name = "card_width_mm", nullable = false)
    private BigDecimal cardWidthMm;

    @Column(name = "card_height_mm", nullable = false)
    private BigDecimal cardHeightMm;

    @Column(name = "card_orientation", nullable = false)
    private String cardOrientation = "LANDSCAPE";

    // Lề giấy và khoảng cách giữa các thẻ (mm); cropMarks: vẽ đường viền cắt quanh mỗi thẻ.
    @Column(name = "margin_mm", nullable = false)
    private BigDecimal marginMm = BigDecimal.TEN;

    @Column(name = "gap_mm", nullable = false)
    private BigDecimal gapMm = BigDecimal.ZERO;

    @Column(name = "crop_marks", nullable = false)
    private Boolean cropMarks = Boolean.TRUE;

    // Có mặt sau hay không; mặt sau cũng có ảnh nền + bố cục (chữ, mã QR) riêng.
    @Column(name = "has_back", nullable = false)
    private Boolean hasBack = Boolean.FALSE;

    // Ảnh nền lưu dạng data URL base64 (giống cấu hình chứng nhận).
    @Column(name = "front_background", columnDefinition = "text")
    private String frontBackground;

    @Column(name = "back_background", columnDefinition = "text")
    private String backBackground;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "front_layout", columnDefinition = "jsonb")
    private List<CertificateLayoutItem> frontLayout = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "back_layout", columnDefinition = "jsonb")
    private List<CertificateLayoutItem> backLayout = new ArrayList<>();
}
