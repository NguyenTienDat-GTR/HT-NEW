package phucuong.ht.modules.privacy.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Lớp cha cho request quản trị viên nhập dữ liệu của người khác: phải xác nhận người đó đã được thông báo và đồng ý. */
@Getter
@Setter
public abstract class SubjectConsentRequest {

    @NotNull(message = "Vui lòng xác nhận người có thông tin đã được thông báo và đồng ý")
    @AssertTrue(message = "Vui lòng xác nhận người có thông tin đã được thông báo và đồng ý")
    private Boolean subjectConsentConfirmed;
}
