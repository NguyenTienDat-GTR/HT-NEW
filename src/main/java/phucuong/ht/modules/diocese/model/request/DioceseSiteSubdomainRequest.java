package phucuong.ht.modules.diocese.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DioceseSiteSubdomainRequest {

    @NotBlank(message = "Subdomain không được để trống")
    @Size(max = 63, message = "Subdomain không được vượt quá 63 ký tự")
    private String siteSubdomain;
}
