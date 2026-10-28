package phucuong.ht.modules.deanery.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.model.request.OrganizationUnitCreateRequest;

import java.util.UUID;

@Getter
@Setter
public class DeaneryCreateRequest extends OrganizationUnitCreateRequest {

    @NotNull(message = "ID giáo phận là bắt buộc")
    private UUID dioceseId;

    // Không bắt buộc: nhiều giáo hạt chưa thành lập hiệp đoàn.
    private String associationName;
}
