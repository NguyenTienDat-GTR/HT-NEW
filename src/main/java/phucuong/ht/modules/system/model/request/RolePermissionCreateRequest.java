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
}
