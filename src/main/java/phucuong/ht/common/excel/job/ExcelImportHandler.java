package phucuong.ht.common.excel.job;

import org.springframework.security.core.Authentication;
import phucuong.ht.common.excel.AvatarZip;
import phucuong.ht.common.excel.ImportSummary;

import java.nio.file.Path;

/** Chiến lược nhập Excel cho một loại tác vụ nền (chạy trong transaction do worker mở). */
public interface ExcelImportHandler {

    ExcelJobKind kind();

    ImportSummary run(String target, Path file, Authentication authentication, JobProgress progress, AvatarZip images);

    @FunctionalInterface
    interface Importer {
        ImportSummary run(String target, Path file, Authentication authentication, JobProgress progress, AvatarZip images);
    }

    static ExcelImportHandler of(ExcelJobKind kind, Importer importer) {
        return new ExcelImportHandler() {
            @Override
            public ExcelJobKind kind() {
                return kind;
            }

            @Override
            public ImportSummary run(String target, Path file, Authentication authentication, JobProgress progress, AvatarZip images) {
                return importer.run(target, file, authentication, progress, images);
            }
        };
    }
}
