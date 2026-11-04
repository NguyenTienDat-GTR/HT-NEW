package phucuong.ht.modules.certificate.config.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.modules.certificate.config.model.CertificateLayoutItem;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Xuất PDF xem trước từ bố cục đang soạn (kể cả chưa lưu), điền bằng dữ liệu mẫu do trình soạn gửi lên. */
@Getter
@Setter
public class CertificatePreviewPdfRequest {

    private String paperSize;
    private String orientation;
    private UUID backgroundAssetId;
    private List<CertificateLayoutItem> fieldLayout;
    // Giá trị mẫu theo mã trường (leaderFullName, courseName...). Trường thiếu thì để trống.
    private Map<String, String> values;
}
