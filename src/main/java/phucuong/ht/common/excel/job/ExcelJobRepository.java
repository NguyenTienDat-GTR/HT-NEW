package phucuong.ht.common.excel.job;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Truy cập bảng excel_job bằng SQL thuần. Mọi chuyển trạng thái là câu UPDATE có điều kiện trạng thái hiện tại
 * (compare-and-set), nên nhiều worker / nhiều node cùng chạy không dẫm lên nhau.
 */
@Repository
@RequiredArgsConstructor
public class ExcelJobRepository {

    public record Row(UUID id, String owner, ExcelJobKind kind, String target, String idempotencyKey, String payloadHash, String status,
                      int processed, int total, String paramsJson, String principalJson, String inputPath, String outputPath,
                      String outputName, String resultJson, String errorMessage, String warning, int attempts, boolean cancelRequested,
                      String imagesPath, String avatarStatus, String avatarPlan, OffsetDateTime avatarLeaseUntil,
                      OffsetDateTime createdAt, OffsetDateTime startedAt, OffsetDateTime finishedAt,
                      String pendingInputPath, String pendingImagesPath) {
    }

    private static final String COLUMNS = "id, owner, kind, target, idempotency_key, payload_hash, status, processed, total, params_json, "
            + "principal_json, input_path, output_path, output_name, result_json, error_message, warning, attempts, cancel_requested, "
            + "images_path, avatar_status, avatar_plan::text as avatar_plan, avatar_lease_until, created_at, started_at, finished_at, "
            + "pending_input_path, pending_images_path";

    private static final RowMapper<Row> MAPPER = (ResultSet rs, int i) -> map(rs);

    private final JdbcTemplate jdbc;

    private static Row map(ResultSet rs) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getString("owner"), ExcelJobKind.valueOf(rs.getString("kind")),
                rs.getString("target"), rs.getString("idempotency_key"), rs.getString("payload_hash"), rs.getString("status"),
                rs.getInt("processed"), rs.getInt("total"), rs.getString("params_json"), rs.getString("principal_json"),
                rs.getString("input_path"), rs.getString("output_path"), rs.getString("output_name"), rs.getString("result_json"),
                rs.getString("error_message"), rs.getString("warning"), rs.getInt("attempts"), rs.getBoolean("cancel_requested"),
                rs.getString("images_path"), rs.getString("avatar_status"), rs.getString("avatar_plan"),
                rs.getObject("avatar_lease_until", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class), rs.getString("pending_input_path"), rs.getString("pending_images_path"));
    }

    /** Tạo job; trả rỗng nếu (owner, kind, key) đã có job (idempotent). Có thể chờ nếu một request cùng key đang commit. */
    public Optional<UUID> insert(String owner, ExcelJobKind kind, String target, String key, String hash, String paramsJson,
                                 String principalJson, String inputPath, String imagesPath, String warning) {
        List<UUID> ids = jdbc.query(
                "insert into excel_job (owner, kind, target, idempotency_key, payload_hash, status, params_json, principal_json, input_path, images_path, warning) "
                        + "values (?, ?, ?, ?, ?, 'QUEUED', ?, ?, ?, ?, ?) on conflict (owner, kind, idempotency_key) do nothing returning id",
                (rs, i) -> rs.getObject(1, UUID.class), owner, kind.name(), target, key, hash, paramsJson, principalJson, inputPath, imagesPath, warning);
        return ids.stream().findFirst();
    }

    public Optional<Row> findById(UUID id) {
        return jdbc.query("select " + COLUMNS + " from excel_job where id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<Row> findByKey(String owner, ExcelJobKind kind, String key) {
        return jdbc.query("select " + COLUMNS + " from excel_job where owner = ? and kind = ? and idempotency_key = ?", MAPPER, owner, kind.name(), key)
                .stream().findFirst();
    }

    public Optional<Row> findActive(String owner, ExcelJobKind kind, String target) {
        return jdbc.query("select " + COLUMNS + " from excel_job where owner = ? and kind = ? and target = ? and status in ('QUEUED','RUNNING')",
                MAPPER, owner, kind.name(), target).stream().findFirst();
    }

    /** Lần import thành công gần nhất cùng nội dung (cùng người, loại, đích và băm file): để cảnh báo import lại. */
    public Optional<OffsetDateTime> findLastSucceeded(String owner, ExcelJobKind kind, String target, String hash) {
        return jdbc.query("select finished_at from excel_job where owner = ? and kind = ? and target = ? and payload_hash = ? and status = 'SUCCEEDED' "
                        + "order by finished_at desc limit 1",
                (rs, i) -> rs.getObject(1, OffsetDateTime.class), owner, kind.name(), target, hash).stream().findFirst();
    }

    public List<Row> listByOwner(String owner, int limit) {
        return jdbc.query("select " + COLUMNS + " from excel_job where owner = ? order by created_at desc limit ?", MAPPER, owner, limit);
    }

    /** Lấy một job QUEUED cũ nhất sang RUNNING (SKIP LOCKED: nhiều worker không lấy trùng một job). */
    public Optional<Row> claimNext(int leaseSeconds) {
        return jdbc.query("update excel_job set status = 'RUNNING', started_at = coalesce(started_at, now()), attempts = attempts + 1, "
                        + "lease_until = now() + make_interval(secs => ?) "
                        + "where id = (select id from excel_job where status = 'QUEUED' order by created_at for update skip locked limit 1) "
                        + "returning " + COLUMNS,
                MAPPER, leaseSeconds).stream().findFirst();
    }

    /** Cập nhật tiến độ và gia hạn lease; trả về cờ người dùng yêu cầu hủy. */
    public boolean touch(UUID id, int processed, int total, int leaseSeconds) {
        List<Boolean> flags = jdbc.query("update excel_job set processed = ?, total = ?, lease_until = now() + make_interval(secs => ?) "
                        + "where id = ? and status = 'RUNNING' returning cancel_requested",
                (rs, i) -> rs.getBoolean(1), processed, total, leaseSeconds, id);
        return !flags.isEmpty() && flags.get(0);
    }

    public int markSucceeded(UUID id, String resultJson, String outputPath, String outputName, Integer processed, Integer total,
                             String avatarPlan, String avatarStatus) {
        return jdbc.update("update excel_job set status = 'SUCCEEDED', result_json = ?, output_path = ?, output_name = ?, processed = coalesce(?, processed), total = coalesce(?, total), "
                        + "finished_at = now(), lease_until = null, input_path = null, avatar_plan = ?::jsonb, avatar_status = ?, "
                        + "images_path = case when ? = 'DONE' then null else images_path end where id = ? and status = 'RUNNING'",
                resultJson, outputPath, outputName, processed, total, avatarPlan, avatarStatus, avatarStatus, id);
    }

    /** Lưu đường dẫn bản sao file của lần import còn dòng nghi trùng chờ xác nhận. */
    public int markPending(UUID id, String inputPath, String imagesPath) {
        return jdbc.update("update excel_job set pending_input_path = ?, pending_images_path = ? where id = ?", inputPath, imagesPath, id);
    }

    /** Các dòng nghi trùng đã được chuyển sang một job xử lý quyết định: job này không còn giữ file. */
    public int clearPending(UUID id) {
        return jdbc.update("update excel_job set pending_input_path = null, pending_images_path = null where id = ?", id);
    }

    public Optional<Row> claimNextAvatar(int leaseSeconds) {
        return jdbc.query("update excel_job set avatar_status = 'RUNNING', avatar_lease_until = now() + make_interval(secs => ?) "
                        + "where id = (select id from excel_job where status = 'SUCCEEDED' and "
                        + "(avatar_status = 'PENDING' or (avatar_status = 'RUNNING' and avatar_lease_until < now())) "
                        + "order by finished_at for update skip locked limit 1) returning " + COLUMNS,
                MAPPER, leaseSeconds).stream().findFirst();
    }

    public int touchAvatar(UUID id, String resultJson, int leaseSeconds) {
        return jdbc.update("update excel_job set result_json = ?, avatar_lease_until = now() + make_interval(secs => ?) "
                + "where id = ? and avatar_status = 'RUNNING'", resultJson, leaseSeconds, id);
    }

    public int finishAvatar(UUID id, String resultJson) {
        return jdbc.update("update excel_job set result_json = ?, avatar_status = 'DONE', avatar_lease_until = null, images_path = null "
                + "where id = ? and avatar_status = 'RUNNING'", resultJson, id);
    }

    public int markFailed(UUID id, String message, String resultJson) {
        return jdbc.update("update excel_job set status = 'FAILED', error_message = ?, result_json = ?, finished_at = now(), lease_until = null, images_path = null "
                + "where id = ? and status = 'RUNNING'", message, resultJson, id);
    }

    public int markCancelled(UUID id) {
        return jdbc.update("update excel_job set status = 'CANCELLED', finished_at = now(), lease_until = null where id = ? and status in ('QUEUED','RUNNING')", id);
    }

    public int requeue(UUID id) {
        return jdbc.update("update excel_job set status = 'QUEUED', lease_until = null where id = ? and status = 'RUNNING'", id);
    }

    /** Hủy: job còn xếp hàng thì hủy ngay; đang chạy thì chỉ đặt cờ (worker kiểm tra ở lần báo tiến độ kế tiếp). Trả về trạng thái sau cùng. */
    public Optional<String> requestCancel(UUID id, String owner, boolean cancellableWhileRunning) {
        int cancelled = jdbc.update("update excel_job set status = 'CANCELLED', finished_at = now() where id = ? and owner = ? and status = 'QUEUED'", id, owner);
        if (cancelled == 1) {
            return Optional.of("CANCELLED");
        }
        if (cancellableWhileRunning) {
            int flagged = jdbc.update("update excel_job set cancel_requested = true where id = ? and owner = ? and status = 'RUNNING'", id, owner);
            if (flagged == 1) {
                return Optional.of("RUNNING");
            }
        }
        return Optional.empty();
    }

    /** Job RUNNING hết lease (worker chết / restart): chạy lại nếu còn lượt, hết lượt thì báo lỗi. Trả về số job bị ảnh hưởng. */
    public int recoverStale(int maxAttempts) {
        int requeued = jdbc.update("update excel_job set status = 'QUEUED', lease_until = null where status = 'RUNNING' and lease_until < now() and attempts < ?", maxAttempts);
        int failed = jdbc.update("update excel_job set status = 'FAILED', finished_at = now(), lease_until = null, "
                + "error_message = 'Tác vụ bị gián đoạn nhiều lần (máy chủ khởi động lại), vui lòng thử lại' where status = 'RUNNING' and lease_until < now() and attempts >= ?", maxAttempts);
        return requeued + failed;
    }

    public record Expired(UUID id, String inputPath, String outputPath, String imagesPath, String pendingInputPath, String pendingImagesPath) {
    }

    /** Job đã quá hạn giữ (đã kết thúc hoặc bị bỏ quên): xóa dòng và trả đường dẫn file để xóa file. */
    public List<Expired> deleteExpired(int retentionDays) {
        return jdbc.query("delete from excel_job where created_at < now() - make_interval(days => ?) and status <> 'RUNNING' "
                        + "and avatar_status is distinct from 'RUNNING' "
                        + "returning id, input_path, output_path, images_path, pending_input_path, pending_images_path",
                (rs, i) -> new Expired(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)),
                retentionDays);
    }
}
