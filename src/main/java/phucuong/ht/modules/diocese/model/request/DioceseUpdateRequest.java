package phucuong.ht.modules.diocese.model.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.model.request.OrganizationUnitUpdateRequest;

@Getter
@Setter
public class DioceseUpdateRequest extends OrganizationUnitUpdateRequest {

    @Size(max = 100)
    private String unionName;
}
