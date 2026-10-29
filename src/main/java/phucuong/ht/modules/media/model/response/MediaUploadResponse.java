package phucuong.ht.modules.media.model.response;

import lombok.Builder;
import lombok.Getter;
import phucuong.ht.modules.media.model.enums.MediaCategory;

import java.util.UUID;

@Getter
@Builder
public class MediaUploadResponse {
    private final UUID assetId;
    private final MediaCategory category;
    /** URL đã ký của biến thể hiển thị (xem trước ngay sau khi tải lên). */
    private final String url;
    /** URL đã ký của biến thể nhỏ. */
    private final String thumbUrl;
    private final int width;
    private final int height;
    private final long bytes;
}
