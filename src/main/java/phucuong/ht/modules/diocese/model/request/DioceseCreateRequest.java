package phucuong.ht.modules.diocese.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.model.request.OrganizationUnitCreateRequest;

@Getter
@Setter
public class DioceseCreateRequest extends OrganizationUnitCreateRequest {

    @NotBlank(message = "Tên liên đoàn là bắt buộc")
    @Size(max = 100, message = "Tên liên đoàn không được vượt quá 100 ký tự")
    private String unionName;
}
