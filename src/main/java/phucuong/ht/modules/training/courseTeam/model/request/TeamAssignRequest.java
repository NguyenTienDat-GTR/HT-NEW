package phucuong.ht.modules.training.courseTeam.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Gán đội thủ công cho các hồ sơ đã có trong danh sách tham dự; teamName rỗng = bỏ khỏi đội. */
@Getter
@Setter
public class TeamAssignRequest {

    @Getter
    @Setter
    public static class Item {
        @NotNull(message = "Thiếu mã hồ sơ")
        private String participationId;

        @Size(max = 150, message = "Tên đội tối đa 150 ký tự")
        private String teamName;
    }

    @NotEmpty(message = "Chưa có hồ sơ nào để gán đội")
    @Size(max = 2000, message = "Mỗi lần lưu tối đa 2000 hồ sơ")
    @Valid
    private List<Item> assignments;
}
