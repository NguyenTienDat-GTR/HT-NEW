package phucuong.ht.modules.certificate.config.model.entity;

import jakarta.persistence.*;
import lombok.*;
import phucuong.ht.common.model.BaseModel;

import java.util.UUID;

// Gán một cấu hình trong thư viện cho một loại khóa, trong một phạm vi.
// Một loại khóa chỉ dùng một cấu hình trong mỗi phạm vi; một cấu hình dùng được cho nhiều loại khóa.
@Entity
@Table(name = "certificate_config_assignment")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class CertificateConfigAssignment extends BaseModel {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "course_type_code", nullable = false)
    private String courseTypeCode;

    @Column(name = "scope_key", nullable = false)
    private String scopeKey;

    @Column(name = "config_id", nullable = false)
    private UUID configId;
}
