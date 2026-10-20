package phucuong.ht.modules.certificate.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.certificate.model.enums.CertificateSignerType;

@Getter
@Setter
public class CertificateApproveRequest {
    private String approvalReason;

    // Người ký ghi vào chứng nhận khi duyệt. signerTitle bắt buộc khi signerType = OTHER;
    // CHAPLAIN mặc định chức danh "Linh mục tuyên úy".
    private CertificateSignerType signerType;
    private String signerName;
    private String signerTitle;
}
