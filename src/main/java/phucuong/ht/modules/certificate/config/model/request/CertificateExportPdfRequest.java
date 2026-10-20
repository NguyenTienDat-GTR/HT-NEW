package phucuong.ht.modules.certificate.config.model.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CertificateExportPdfRequest {

    @NotEmpty(message = "Cần chọn ít nhất một chứng nhận để xuất PDF")
    private List<String> certificateCodes;
}
