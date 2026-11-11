package phucuong.ht.common.excel.job;

import org.springframework.security.core.Authentication;
import phucuong.ht.common.excel.AvatarZip;
import phucuong.ht.common.excel.ImportOptions;
import phucuong.ht.common.excel.ImportSummary;

import java.nio.file.Path;

/** Chiến lược nhập Excel cho một loại tác vụ nền (chạy trong transaction do worker mở). */
public interface ExcelImportHandler {

    ExcelJobKind kind();

    ImportSummary run(String target, Path file, Authentication authentication, JobProgress progress, AvatarZip images);

    /** Chạy kèm tùy chọn (quyết định xác nhận hồ sơ nghi trùng); loại nhập không hỗ trợ tùy chọn thì bỏ qua. */
    default ImportSummary run(String target, Path file, Authentication authentication, JobProgress progress, AvatarZip images, ImportOptions options) {
        return run(target, file, authentication, progress, images);
    }

    @FunctionalInterface
    interface Importer {
        ImportSummary run(String target, Path file, Authentication authentication, JobProgress progress, AvatarZip images);
    }

    @FunctionalInterface
    interface OptionsImporter {
        ImportSummary run(String target, Path file, Authentication authentication, JobProgress progress, AvatarZip images, ImportOptions options);
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

    /** Loại nhập có phát hiện hồ sơ huynh trưởng trùng: nhận thêm quyết định xác nhận của người dùng. */
    static ExcelImportHandler ofOptions(ExcelJobKind kind, OptionsImporter importer) {
        return new ExcelImportHandler() {
            @Override
            public ExcelJobKind kind() {
                return kind;
            }

            @Override
            public ImportSummary run(String target, Path file, Authentication authentication, JobProgress progress, AvatarZip images) {
                return importer.run(target, file, authentication, progress, images, ImportOptions.NONE);
            }

            @Override
            public ImportSummary run(String target, Path file, Authentication authentication, JobProgress progress, AvatarZip images, ImportOptions options) {
                return importer.run(target, file, authentication, progress, images, options);
            }
        };
    }
}
