package phucuong.ht.modules.parish.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.model.request.OrganizationUnitCreateRequest;

@Getter
@Setter
public class ParishCreateRequest extends OrganizationUnitCreateRequest {

    @NotBlank
    private String deaneryCode;

    @NotBlank
    private String chapterName;
}
