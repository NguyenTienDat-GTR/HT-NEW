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

    @NotBlank(message = "Tên là bắt buộc")
    @Size(max = 100, message = "Tên không được vượt quá 100 ký tự")
    private String name;

    private LocalDate patronSaintDay;

    private LocalDate establishmentDate;

    @Size(max = 100, message = "Tên tuyên úy không được vượt quá 100 ký tự")
    private String chaplain;

    @Size(max = 15, message = "Số điện thoại không được vượt quá 15 ký tự")
    private String phoneNumber;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String email;
}
