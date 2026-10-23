package phucuong.ht.common.excel.job;

import com.fasterxml.jackson.annotation.JsonInclude;
import phucuong.ht.common.excel.ImportError;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Trạng thái một tác vụ nền Excel trả cho giao diện. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExcelJobResponse(
        UUID id,
        ExcelJobKind kind,
        String label,
        boolean importJob,
        String target,
        String status,
        int processed,
        int total,
        // Cảnh báo (ví dụ file này đã được import trước đó).
        String warning,
        String errorMessage,
        // Danh sách dòng lỗi khi import bị từ chối.
        List<ImportError> errors,
        Integer created,
        String resultMessage,
        boolean downloadable,
        String fileName,
        OffsetDateTime createdAt,
        OffsetDateTime finishedAt,
        OffsetDateTime expiresAt,
        // true: yêu cầu trùng Idempotency-Key nên trả lại job đã có, không tạo job mới.
        boolean replayed
) {
    public ExcelJobResponse withReplayed(boolean value) {
        return new ExcelJobResponse(id, kind, label, importJob, target, status, processed, total, warning, errorMessage, errors, created,
                resultMessage, downloadable, fileName, createdAt, finishedAt, expiresAt, value);
    }
}
