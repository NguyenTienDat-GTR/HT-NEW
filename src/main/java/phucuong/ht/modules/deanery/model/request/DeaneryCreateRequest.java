package phucuong.ht.modules.deanery.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.model.request.OrganizationUnitCreateRequest;

@Getter
@Setter
public class DeaneryCreateRequest extends OrganizationUnitCreateRequest {

    @NotBlank(message = "Mã giáo phận là bắt buộc")
    private String dioceseCode;

    @NotBlank(message = "Tên hiệp đoàn là bắt buộc")
    private String associationName;
}
