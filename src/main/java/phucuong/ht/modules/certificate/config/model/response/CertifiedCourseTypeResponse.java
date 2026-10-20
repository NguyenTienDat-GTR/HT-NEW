package phucuong.ht.modules.certificate.config.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

// Loại khóa có yêu cầu chứng nhận, dùng cho ô chọn phạm vi cấu hình.
@Getter
@AllArgsConstructor
public class CertifiedCourseTypeResponse {
    private String typeCode;
    private String typeName;
}
