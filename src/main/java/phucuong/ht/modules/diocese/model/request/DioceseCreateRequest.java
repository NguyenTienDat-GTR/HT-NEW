package phucuong.ht.modules.diocese.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.model.request.OrganizationUnitCreateRequest;

@Getter
@Setter
public class DioceseCreateRequest extends OrganizationUnitCreateRequest {

    @NotBlank
    @Size(max = 100)
    private String unionName;
}
