package phucuong.ht.common.excel.job;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Tra cứu handler theo {@link ExcelJobKind}. Kiểm tra ngay lúc khởi động: mỗi loại tác vụ phải có đúng một handler
 * (xuất hoặc nhập), tránh lỗi chỉ lộ ra khi người dùng bấm chạy.
 */
@Component
public class ExcelJobHandlerRegistry {

    private final Map<ExcelJobKind, ExcelExportHandler> exports = new EnumMap<>(ExcelJobKind.class);
    private final Map<ExcelJobKind, ExcelImportHandler> imports = new EnumMap<>(ExcelJobKind.class);

    public ExcelJobHandlerRegistry(List<ExcelExportHandler> exportHandlers, List<ExcelImportHandler> importHandlers) {
        for (ExcelExportHandler handler : exportHandlers) {
            if (handler.kind().isImport()) {
                throw new IllegalStateException("Handler xuất đăng ký cho tác vụ nhập: " + handler.kind());
            }
            if (exports.put(handler.kind(), handler) != null) {
                throw new IllegalStateException("Trùng handler xuất cho " + handler.kind());
            }
        }
        for (ExcelImportHandler handler : importHandlers) {
            if (!handler.kind().isImport()) {
                throw new IllegalStateException("Handler nhập đăng ký cho tác vụ xuất: " + handler.kind());
            }
            if (imports.put(handler.kind(), handler) != null) {
                throw new IllegalStateException("Trùng handler nhập cho " + handler.kind());
            }
        }
        for (ExcelJobKind kind : ExcelJobKind.values()) {
            boolean present = kind.isImport() ? imports.containsKey(kind) : exports.containsKey(kind);
            if (!present) {
                throw new IllegalStateException("Chưa có handler cho tác vụ " + kind);
            }
        }
    }

    public ExcelExportHandler exportHandler(ExcelJobKind kind) {
        ExcelExportHandler handler = exports.get(kind);
        if (handler == null) {
            throw new IllegalArgumentException("Không phải tác vụ xuất: " + kind);
        }
        return handler;
    }

    public ExcelImportHandler importHandler(ExcelJobKind kind) {
        ExcelImportHandler handler = imports.get(kind);
        if (handler == null) {
            throw new IllegalArgumentException("Không phải tác vụ nhập: " + kind);
        }
        return handler;
    }
}
