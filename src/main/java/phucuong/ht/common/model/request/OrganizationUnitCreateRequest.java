package phucuong.ht.common.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class OrganizationUnitCreateRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    private LocalDate patronSaintDay;

    private LocalDate establishmentDate;

    @Size(max = 100)
    private String chaplain;

    @Size(max = 15)
    private String phoneNumber;

    @Email
    @Size(max = 100)
    private String email;
}
