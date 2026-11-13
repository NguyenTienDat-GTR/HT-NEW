package phucuong.ht.common.excel.job;

import org.springframework.security.core.Authentication;
import phucuong.ht.common.repository.BaseSearchRequest;

import java.util.List;

/**
 * Chiến lược xuất Excel cho một loại tác vụ nền. Thêm loại xuất mới = khai báo thêm một bean handler,
 * không phải sửa {@link ExcelJobHandlers}.
 */
public interface ExcelExportHandler {

    ExcelJobKind kind();

    ExcelExportFile export(ExcelExportRequest request, Authentication authentication, JobProgress progress);

    /** Phần đầu tên file xuất; handler có thể ghi đè để gắn thêm ngữ cảnh (vd. tên khóa). */
    default String fileBase(ExcelExportRequest request) {
        return kind().fileBase();
    }

    /** Tên các cột người dùng được chọn khi xuất (theo đúng thứ tự trong file); rỗng = loại xuất không cho chọn cột. */
    default List<String> columns() {
        return List.of();
    }

    @FunctionalInterface
    interface Exporter {
        byte[] export(BaseSearchRequest request, Authentication authentication, JobProgress progress);
    }

    @FunctionalInterface
    interface RequestExporter {
        byte[] export(ExcelExportRequest request, Authentication authentication, JobProgress progress);
    }

    /** Handler đơn giản chỉ dùng bộ lọc danh sách (không chọn cột). */
    static ExcelExportHandler of(ExcelJobKind kind, Exporter exporter) {
        return of(kind, List.of(), (request, authentication, progress) -> exporter.export(request.search(), authentication, progress));
    }

    /** Handler xuất .xlsx có danh sách cột cho người dùng chọn. */
    static ExcelExportHandler of(ExcelJobKind kind, List<String> columns, RequestExporter exporter) {
        List<String> selectable = List.copyOf(columns);
        return new ExcelExportHandler() {
            @Override
            public ExcelJobKind kind() {
                return kind;
            }

            @Override
            public ExcelExportFile export(ExcelExportRequest request, Authentication authentication, JobProgress progress) {
                return ExcelExportFile.xlsx(exporter.export(request, authentication, progress));
            }

            @Override
            public List<String> columns() {
                return selectable;
            }
        };
    }
}
