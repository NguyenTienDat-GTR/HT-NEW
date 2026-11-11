package phucuong.ht.common.excel.job;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.excel.AvatarZip;
import phucuong.ht.common.excel.ImportOptions;
import phucuong.ht.common.excel.ImportSummary;
import phucuong.ht.common.repository.BaseSearchRequest;

import java.nio.file.Path;

/**
 * Cửa vào cho worker: chuyển việc cho handler đúng loại trong {@link ExcelJobHandlerRegistry}.
 * Nội dung từng loại nằm ở các {@link ExcelExportHandler} / {@link ExcelImportHandler}.
 */
@Component
@RequiredArgsConstructor
public class ExcelJobHandlers {

    private final ExcelJobHandlerRegistry registry;

    /** Phần đầu tên file xuất. */
    public String exportFileBase(ExcelJobKind kind, BaseSearchRequest request) {
        return registry.exportHandler(kind).fileBase(request);
    }

    public byte[] export(ExcelJobKind kind, BaseSearchRequest request, Authentication authentication, JobProgress progress) {
        return registry.exportHandler(kind).export(request, authentication, progress);
    }

    public ImportSummary runImport(ExcelJobKind kind, String target, Path file, Authentication authentication, JobProgress progress,
                                   AvatarZip images, ImportOptions options) {
        return registry.importHandler(kind).run(target, file, authentication, progress, images, options);
    }
}
