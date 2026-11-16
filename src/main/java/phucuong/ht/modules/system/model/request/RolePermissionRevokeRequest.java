package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RolePermissionRevokeRequest {

    @NotBlank
    private String roleCode;

    @NotEmpty
    private List<@NotBlank String> permissionCodes;

    private java.util.UUID dioceseId;

    private java.util.UUID deaneryId;

    private java.util.UUID parishId;

    /** Nhiều nơi áp dụng cùng lúc: dùng danh sách của cấp cụ thể nhất có giá trị (giáo xứ, rồi giáo hạt, rồi giáo phận); gộp với id đơn lẻ ở trên. */
    private java.util.List<java.util.UUID> dioceseIds;

    private java.util.List<java.util.UUID> deaneryIds;

    private java.util.List<java.util.UUID> parishIds;
}
