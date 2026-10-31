package phucuong.ht.common.excel.job;

import org.springframework.security.core.Authentication;
import phucuong.ht.common.repository.BaseSearchRequest;

/**
 * Chiến lược xuất Excel cho một loại tác vụ nền. Thêm loại xuất mới = khai báo thêm một bean handler,
 * không phải sửa {@link ExcelJobHandlers}.
 */
public interface ExcelExportHandler {

    ExcelJobKind kind();

    byte[] export(BaseSearchRequest request, Authentication authentication, JobProgress progress);

    /** Phần đầu tên file xuất; handler có thể ghi đè để gắn thêm ngữ cảnh (vd. tên khóa). */
    default String fileBase(BaseSearchRequest request) {
        return kind().fileBase();
    }

    @FunctionalInterface
    interface Exporter {
        byte[] export(BaseSearchRequest request, Authentication authentication, JobProgress progress);
    }

    static ExcelExportHandler of(ExcelJobKind kind, Exporter exporter) {
        return new ExcelExportHandler() {
            @Override
            public ExcelJobKind kind() {
                return kind;
            }

            @Override
            public byte[] export(BaseSearchRequest request, Authentication authentication, JobProgress progress) {
                return exporter.export(request, authentication, progress);
            }
        };
    }
}
