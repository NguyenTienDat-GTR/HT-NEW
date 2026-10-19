package phucuong.ht.modules.training.scoreComponentTemplate.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import phucuong.ht.common.enums.UnitType;

import java.math.BigDecimal;

@Getter
@Setter
public class ScoreComponentTemplateCreateRequest {

    // Mã thành phần điểm do hệ thống tự sinh (UUID); client không gửi.

    @NotBlank
    private String name;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal maxScore;

    private String description;

    @NotBlank
    private String courseTypeCode;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal coefficient;

    // Mặc định true nếu không gửi.
    private Boolean includeInFinal;

    private Integer displayOrder;

    @NotNull
    private UnitType unitType;
}
