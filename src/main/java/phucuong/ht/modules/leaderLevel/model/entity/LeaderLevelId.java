package phucuong.ht.modules.leaderLevel.model.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

// Khóa của một cấp độ: mỗi giáo phận có danh mục cấp độ riêng, mã cấp chỉ duy nhất trong giáo phận.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LeaderLevelId implements Serializable {
    private UUID dioceseId;
    private String levelCode;
}
