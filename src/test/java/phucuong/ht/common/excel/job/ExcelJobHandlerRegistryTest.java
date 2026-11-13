package phucuong.ht.common.excel.job;

import org.junit.jupiter.api.Test;
import phucuong.ht.common.repository.BaseSearchRequest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExcelJobHandlerRegistryTest {

    private static List<ExcelExportHandler> allExports() {
        List<ExcelExportHandler> list = new ArrayList<>();
        for (ExcelJobKind kind : ExcelJobKind.values()) {
            if (!kind.isImport()) {
                list.add(ExcelExportHandler.of(kind, (request, auth, progress) -> new byte[]{1}));
            }
        }
        return list;
    }

    private static List<ExcelImportHandler> allImports() {
        List<ExcelImportHandler> list = new ArrayList<>();
        for (ExcelJobKind kind : ExcelJobKind.values()) {
            if (kind.isImport()) {
                list.add(ExcelImportHandler.of(kind, (target, file, auth, progress, images) -> null));
            }
        }
        return list;
    }

    @Test
    void resolvesHandlerByKind() {
        List<ExcelExportHandler> exports = allExports();
        ExcelJobHandlerRegistry registry = new ExcelJobHandlerRegistry(exports, allImports());

        ExcelExportHandler handler = registry.exportHandler(ExcelJobKind.EXPORT_LEADERS);

        assertSame(ExcelJobKind.EXPORT_LEADERS, handler.kind());
        assertEquals(ExcelJobKind.EXPORT_LEADERS.fileBase(), handler.fileBase(ExcelExportRequest.of(new BaseSearchRequest())));
        assertSame(ExcelJobKind.IMPORT_LEADERS, registry.importHandler(ExcelJobKind.IMPORT_LEADERS).kind());
    }

    @Test
    void failsFastWhenKindHasNoHandler() {
        List<ExcelExportHandler> exports = allExports();
        exports.removeIf(handler -> handler.kind() == ExcelJobKind.EXPORT_TEAMS);

        assertThrows(IllegalStateException.class, () -> new ExcelJobHandlerRegistry(exports, allImports()));
    }

    @Test
    void rejectsDuplicateHandlers() {
        List<ExcelExportHandler> exports = allExports();
        exports.add(ExcelExportHandler.of(ExcelJobKind.EXPORT_COURSES, (request, auth, progress) -> new byte[0]));

        assertThrows(IllegalStateException.class, () -> new ExcelJobHandlerRegistry(exports, allImports()));
    }

    @Test
    void rejectsWrongDirectionLookup() {
        ExcelJobHandlerRegistry registry = new ExcelJobHandlerRegistry(allExports(), allImports());

        assertThrows(IllegalArgumentException.class, () -> registry.exportHandler(ExcelJobKind.IMPORT_LEADERS));
        assertThrows(IllegalArgumentException.class, () -> registry.importHandler(ExcelJobKind.EXPORT_LEADERS));
    }
}
