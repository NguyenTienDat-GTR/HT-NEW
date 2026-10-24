package phucuong.ht.modules.badge.export.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BadgeExportJobResponse {
    private String jobId;
    // PENDING | DONE | FAILED
    private String status;
    private String filename;
    private Integer badgeCount;
    private Integer pageCount;
    private String error;
}
