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

// Cấu hình chứng nhận cho một loại khóa có yêu cầu chứng nhận.
// courseTypeCode = null nghĩa là cấu hình chung mặc định, dùng khi loại khóa chưa có cấu hình riêng.
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

    // null = cấu hình chung mặc định.
    @Column(name = "course_type_code")
    private String courseTypeCode;

    // Khổ giấy (A3/A4/A5/LETTER/LEGAL) và hướng in (PORTRAIT/LANDSCAPE).
    @Column(name = "paper_size")
    private String paperSize;

    @Column(name = "orientation")
    private String orientation;

    // Ảnh nền chứng nhận lưu dạng data URL base64.
    @Column(name = "background_image", columnDefinition = "text")
    private String backgroundImage;

    // Danh sách phần tử (trường cố định + văn bản tĩnh) đặt trên ảnh nền, lưu JSONB.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_layout", columnDefinition = "jsonb")
    private List<CertificateLayoutItem> fieldLayout = new ArrayList<>();
}
