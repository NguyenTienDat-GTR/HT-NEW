package phucuong.ht.modules.training.courseTeam.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TeamDivideRequest {

    public enum NamingMode { SEQUENTIAL, CUSTOM }

    // RANDOM: chia ngẫu nhiên; UNIT_LIMIT: mỗi đội chỉ gồm hồ sơ của tối đa N đơn vị (giáo hạt với khóa giáo phận, giáo xứ với khóa giáo hạt).
    public enum AssignmentMode { RANDOM, UNIT_LIMIT }

    @NotNull(message = "Số đội cần chia là bắt buộc")
    @Min(value = 1, message = "Số đội phải từ 1 trở lên")
    @Max(value = 500, message = "Tối đa 500 đội")
    private Integer teamCount;

    // Số hồ sơ tối đa của mỗi đội; số hồ sơ thực tế mỗi đội chênh nhau không quá 1.
    @NotNull(message = "Số hồ sơ mỗi đội là bắt buộc")
    @Min(value = 1, message = "Số hồ sơ mỗi đội phải từ 1 trở lên")
    private Integer teamSize;

    @NotNull(message = "Cách đặt tên đội là bắt buộc")
    private NamingMode namingMode;

    // Bắt buộc khi namingMode = CUSTOM: đủ teamCount tên, không trùng nhau.
    private List<String> teamNames;

    @NotNull(message = "Cách chia hồ sơ vào đội là bắt buộc")
    private AssignmentMode assignmentMode;

    // Bắt buộc khi assignmentMode = UNIT_LIMIT.
    @Min(value = 1, message = "Số đơn vị tối đa mỗi đội phải từ 1 trở lên")
    private Integer maxUnitsPerTeam;
}
