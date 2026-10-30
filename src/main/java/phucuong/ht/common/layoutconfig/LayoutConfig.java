package phucuong.ht.common.layoutconfig;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Cấu hình bố cục trong thư viện (chứng nhận, thẻ tham dự): thuộc một phạm vi, mỗi phạm vi có đúng một cấu hình chung. */
public interface LayoutConfig {

    UUID getId();

    String getScopeKey();

    Boolean getIsDefault();

    void setIsDefault(Boolean isDefault);

    void setUpdatedAt(OffsetDateTime updatedAt);

    void setUpdatedBy(String updatedBy);
}
