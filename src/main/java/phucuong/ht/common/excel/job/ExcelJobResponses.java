package phucuong.ht.common.excel.job;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import phucuong.ht.common.dto.apiResponse;

/** Phản hồi khi tạo tác vụ nền: 202 Accepted cho job mới, 200 + Idempotent-Replay khi trả lại job đã có (cùng Idempotency-Key). */
public final class ExcelJobResponses {

    private ExcelJobResponses() {
    }

    public static ResponseEntity<apiResponse<ExcelJobResponse>> accepted(ExcelJobResponse job) {
        if (job.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replay", "true").body(apiResponse.success("Tác vụ đã được tạo trước đó", job));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(apiResponse.success("Đã nhận yêu cầu, đang xử lý nền", job));
    }
}
