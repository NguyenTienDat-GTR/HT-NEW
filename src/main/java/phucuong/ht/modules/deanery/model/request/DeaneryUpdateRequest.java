package phucuong.ht.modules.deanery.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.model.request.OrganizationUnitUpdateRequest;

@Getter
@Setter
public class DeaneryUpdateRequest extends OrganizationUnitUpdateRequest {

    private String associationName;
}
