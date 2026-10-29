package phucuong.ht.modules.analytics.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter @Builder
public class AnalyticsFilterOptionsResponse {
    private final List<Option> deaneries;
    private final List<Option> parishes;
    private final List<Option> courseTypes;
    private final List<Option> courses;
    private final List<Option> leaderLevels;

    /** parent: giáo hạt của giáo xứ, hoặc loại khóa của khóa huấn luyện, để FE lọc phụ thuộc. */
    @Getter @Builder public static class Option { private final String value; private final String label; private final String parent; }
}
