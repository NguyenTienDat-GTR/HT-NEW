package phucuong.ht.modules.analytics.model.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WorkQueueItemResponse {
    private String key;
    private String title;
    private String description;
    private String href;
    private long count;
    private String icon;
    private String tone;
}
