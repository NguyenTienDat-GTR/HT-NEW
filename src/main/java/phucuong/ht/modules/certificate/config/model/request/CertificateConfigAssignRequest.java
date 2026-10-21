package phucuong.ht.modules.certificate.config.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CertificateConfigAssignRequest {

    @NotBlank(message = "Thiếu mã loại khóa")
    private String courseTypeCode;

    // null = bỏ gán, loại khóa quay về dùng cấu hình chung.
    private UUID configId;
}
