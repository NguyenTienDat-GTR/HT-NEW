package phucuong.ht.modules.analytics.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter @Builder(toBuilder = true)
public class AnalyticsRecordsResponse {
    private final String dataset;
    private final List<String> columns;
    private final List<Map<String, Object>> rows;
    private final long total;
    private final int page;
    private final int size;
    /** Chỉ có ở bản xuất: dòng GIÁO PHẬN / GIÁO HẠT / GIÁO XỨ cho phần đầu file (theo bộ lọc, không lọc thì theo tài khoản). */
    private final List<String> unitLines;
}
