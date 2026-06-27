package phucuong.ht.modules.deanery.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.model.request.OrganizationUnitCreateRequest;

@Getter
@Setter
public class DeaneryCreateRequest extends OrganizationUnitCreateRequest {

    @NotBlank
    private String dioceseCode;

    @NotBlank
    private String associationName;
}
