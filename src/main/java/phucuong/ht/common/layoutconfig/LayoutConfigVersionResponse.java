package phucuong.ht.common.layoutconfig;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Một phiên bản đã lưu của cấu hình chứng nhận / thẻ. snapshot gồm nội dung cấu hình và URL ảnh nền còn dùng được. */
public record LayoutConfigVersionResponse(UUID id, int versionNo, OffsetDateTime createdAt, String createdBy, Map<String, Object> snapshot) {
}
