package phucuong.ht.modules.parish.model.request;

import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.model.request.OrganizationUnitUpdateRequest;

import java.util.UUID;

@Getter
@Setter
public class ParishUpdateRequest extends OrganizationUnitUpdateRequest {

    private UUID deaneryId;

    private String chapterName;
}
