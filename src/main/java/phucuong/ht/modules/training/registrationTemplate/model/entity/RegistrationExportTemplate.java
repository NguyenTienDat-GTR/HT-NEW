package phucuong.ht.modules.training.registrationTemplate.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import phucuong.ht.common.model.BaseModel;

import java.util.UUID;

/** Mẫu Excel danh sách đăng ký tham dự của một đơn vị (giáo phận hoặc giáo hạt); mỗi đơn vị một mẫu. */
@Entity
@Table(name = "registration_export_template")
@Getter
@Setter
@NoArgsConstructor
public class RegistrationExportTemplate extends BaseModel {

    @Id
    @Column(name = "id")
    private UUID id;

    /** DIOCESE hoặc DEANERY. */
    @Column(name = "unit_type", nullable = false)
    private String unitType;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** File .xlsx nằm ở kho lưu trữ (Cloudinary); đây chỉ lưu nơi chứa. Ảnh logo nằm ngay trong file Excel, không lưu riêng. */
    @Column(name = "storage_provider", nullable = false)
    private String storageProvider;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "compressed_size")
    private Long compressedSize;
}
