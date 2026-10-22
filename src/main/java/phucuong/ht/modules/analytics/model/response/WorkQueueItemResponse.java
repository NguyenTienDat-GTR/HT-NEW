package phucuong.ht.modules.analytics.model.response;

import lombok.Builder;
import lombok.Getter;

/** key xác định nội dung hiển thị phía FE; BE chỉ trả số lượng, đường dẫn xử lý và mức khẩn cấp. */
@Getter
@Builder
public class WorkQueueItemResponse {
    private String key;
    private String href;
    private long count;
    private String severity;
}
