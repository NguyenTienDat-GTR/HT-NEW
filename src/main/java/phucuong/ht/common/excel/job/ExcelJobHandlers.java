package phucuong.ht.common.excel.job;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.excel.AvatarZip;
import phucuong.ht.common.excel.ImportOptions;
import phucuong.ht.common.excel.ImportSummary;

import java.nio.file.Path;
import java.util.List;

/**
 * Cửa vào cho worker: chuyển việc cho handler đúng loại trong {@link ExcelJobHandlerRegistry}.
 * Nội dung từng loại nằm ở các {@link ExcelExportHandler} / {@link ExcelImportHandler}.
 */
@Component
@RequiredArgsConstructor
public class ExcelJobHandlers {

    private final ExcelJobHandlerRegistry registry;

    /** Phần đầu tên file xuất. */
    public String exportFileBase(ExcelJobKind kind, ExcelExportRequest request) {
        return registry.exportHandler(kind).fileBase(request);
    }

    public ExcelExportFile export(ExcelJobKind kind, ExcelExportRequest request, Authentication authentication, JobProgress progress) {
        return registry.exportHandler(kind).export(request, authentication, progress);
    }

    /** Các cột chọn được của một loại xuất. */
    public List<String> exportColumns(ExcelJobKind kind) {
        return registry.exportHandler(kind).columns();
    }

    public ImportSummary runImport(ExcelJobKind kind, String target, Path file, Authentication authentication, JobProgress progress,
                                   AvatarZip images, ImportOptions options) {
        return registry.importHandler(kind).run(target, file, authentication, progress, images, options);
    }
}
