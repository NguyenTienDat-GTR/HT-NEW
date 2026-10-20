package phucuong.ht.modules.certificate.config.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;
import phucuong.ht.modules.certificate.model.enums.CertificateSignerType;

import java.util.List;

@Getter
@Setter
public class CertificateConfigUpsertRequest {

    // null = cấu hình chung mặc định.
    private String courseTypeCode;

    @NotNull(message = "Loại người ký là bắt buộc")
    private CertificateSignerType signerType;

    @NotBlank(message = "Tên người ký là bắt buộc")
    private String signerName;

    // Bắt buộc khi signerType = OTHER (kiểm tra trong service).
    private String signerTitle;

    // Ảnh nền dạng data URL base64.
    private String backgroundImage;

    private List<CertificateLayoutItem> fieldLayout;
}
