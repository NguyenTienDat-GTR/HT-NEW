package phucuong.ht.modules.parish.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.model.request.OrganizationUnitCreateRequest;
import phucuong.ht.modules.parish.model.enums.ParishUnitType;

import java.util.UUID;

@Getter
@Setter
public class ParishCreateRequest extends OrganizationUnitCreateRequest {

    @NotNull
    private UUID deaneryId;

    @NotBlank
    private String chapterName;

    // Mặc định là giáo xứ. Giáo họ / giáo điểm bắt buộc có parentParishId (giáo xứ cha cùng giáo hạt).
    private ParishUnitType unitType;

    private UUID parentParishId;
}
