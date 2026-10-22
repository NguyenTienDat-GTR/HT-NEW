package phucuong.ht.modules.analytics.model.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/** Bộ lọc dùng chung cộng với dataset và phần tử biểu đồ được chọn để xem chi tiết hoặc xuất. */
@Getter @Setter
public class AnalyticsRecordRequest extends AnalyticsFilterRequest {
    public static final Set<String> DATASETS = Set.of("leaders", "participations", "certificates");
    public static final int MAX_PAGE_SIZE = 200;
    public static final int MAX_EXPORT_ROWS = 20000;
    private String dataset = "participations";
    // Phần tử biểu đồ được bấm: dimension (vd. gender, deanery, funnel) và giá trị của nó.
    private String dimension;
    private String value;
    private int page = 0;
    private int size = 20;

    @Override public void validate() {
        super.validate();
        if (!DATASETS.contains(dataset)) throw new IllegalArgumentException("dataset không hợp lệ");
        if (page < 0) throw new IllegalArgumentException("page không hợp lệ");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new IllegalArgumentException("size phải từ 1 đến " + MAX_PAGE_SIZE);
        if ((dimension == null) != (value == null)) throw new IllegalArgumentException("dimension và value phải đi cùng nhau");
    }

    @Override public String cacheKey() { return super.cacheKey(); }
}
