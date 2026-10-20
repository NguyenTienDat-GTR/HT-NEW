package phucuong.ht.modules.certificate.model.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class CertificateBulkApprovalRequest {
    @NotEmpty
    private List<String> ids;

    @NotNull
    private Boolean approved;

    // Lý do chung (duyệt ngoại lệ hoặc từ chối) khi hồ sơ không có lý do riêng.
    private String reason;

    // Lý do riêng theo mã chứng nhận; ưu tiên hơn lý do chung.
    private Map<String, String> reasons;

    // Người ký áp dụng cho tất cả chứng nhận được duyệt trong lần này.
    private phucuong.ht.modules.certificate.model.enums.CertificateSignerType signerType;
    private String signerName;
    private String signerTitle;
}
