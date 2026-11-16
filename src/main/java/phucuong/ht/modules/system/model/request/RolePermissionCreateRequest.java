package phucuong.ht.modules.system.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class RolePermissionCreateRequest {

    @NotBlank
    private String roleCode;

    @NotBlank
    private String permissionCode;


    private String conditions;

    private String assignedBy;

    private OffsetDateTime assignedAt;

    private OffsetDateTime expiresAt;

    /** Nơi áp dụng (gửi một đơn vị cụ thể nhất); bỏ trống = mặc định cho vai trò (hoặc cả đơn vị của người thao tác). */
    private java.util.UUID dioceseId;

    private java.util.UUID deaneryId;

    private java.util.UUID parishId;

    /** Nhiều nơi áp dụng cùng lúc: dùng danh sách của cấp cụ thể nhất có giá trị (giáo xứ, rồi giáo hạt, rồi giáo phận); gộp với id đơn lẻ ở trên. */
    private java.util.List<java.util.UUID> dioceseIds;

    private java.util.List<java.util.UUID> deaneryIds;

    private java.util.List<java.util.UUID> parishIds;
}
