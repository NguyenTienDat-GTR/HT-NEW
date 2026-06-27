package phucuong.ht.modules.parish.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.model.request.OrganizationUnitUpdateRequest;

@Getter
@Setter
public class ParishUpdateRequest extends OrganizationUnitUpdateRequest {

    private String deaneryCode;

    private String chapterName;
}
