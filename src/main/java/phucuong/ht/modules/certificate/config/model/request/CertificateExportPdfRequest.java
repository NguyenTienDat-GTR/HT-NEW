package phucuong.ht.modules.certificate.config.model.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CertificateExportPdfRequest {

    // Danh sách chứng nhận được chọn. Bỏ trống kèm courseCode thì xuất mọi chứng nhận đã duyệt của khóa đó.
    private List<String> certificateCodes;

    private String courseCode;
}
