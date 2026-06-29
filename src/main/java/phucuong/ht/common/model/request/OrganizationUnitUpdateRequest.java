package phucuong.ht.common.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class OrganizationUnitUpdateRequest {

    @Size(max = 100, message = "Tên không được vượt quá 100 ký tự")
    private String name;

    private LocalDate patronSaintDay;

    private LocalDate establishmentDate;

    @Size(max = 100, message = "Tên tuyên úy không được vượt quá 100 ký tự")
    private String chaplain;

    @Pattern(regexp = "^$|^0[1-9]\\d{8}$", message = "Số điện thoại phải là số điện thoại Việt Nam gồm 10 chữ số và bắt đầu bằng 0")
    private String phoneNumber;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String email;
}
