package phucuong.ht.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Idempotency cho thao tác ghi: cùng (người gọi, phạm vi, Idempotency-Key) chỉ thực hiện một lần, gửi lại nhận đúng kết quả cũ.
 *
 * <p>Bản ghi được ghi CÙNG transaction với thao tác (nên thao tác lỗi/rollback thì key không bị giữ). Hai request cùng key đến
 * đồng thời: câu {@code INSERT ... ON CONFLICT DO NOTHING} của request sau chờ request trước commit hoặc rollback, rồi mới quyết định
 * chạy (trước rollback) hay phát lại kết quả đã lưu (trước commit).
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    public static final String HEADER = "Idempotency-Key";
    private static final Pattern KEY_FORMAT = Pattern.compile("^[A-Za-z0-9._:-]{8,100}$");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /** body: kết quả mới hoặc kết quả đã lưu (JsonNode) khi phát lại. */
    public record Outcome(Object body, boolean replayed) {
    }

    public static String requireValidKey(String key) {
        if (key == null || !KEY_FORMAT.matcher(key.trim()).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                    "Header Idempotency-Key là bắt buộc (8-100 ký tự gồm chữ, số, '.', '_', ':', '-')");
        }
        return key.trim();
    }

    /** Key trống thì chạy thẳng (không có bảo vệ phát lại, nhưng các khóa hàng vẫn chống race). */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> Outcome execute(String owner, String scope, String key, Object payload, Supplier<T> action) {
        if (key == null || key.isBlank()) {
            return new Outcome(action.get(), false);
        }
        String validKey = requireValidKey(key);
        String hash = sha256(json(payload));
        int inserted = jdbc.update(
                "insert into idempotency_record (owner, scope, idempotency_key, payload_hash) values (?, ?, ?, ?) "
                        + "on conflict (owner, scope, idempotency_key) do nothing",
                owner, scope, validKey, hash);
        if (inserted == 0) {
            return replay(owner, scope, validKey, hash);
        }
        T result = action.get();
        jdbc.update("update idempotency_record set response_json = ? where owner = ? and scope = ? and idempotency_key = ?",
                json(result), owner, scope, validKey);
        return new Outcome(result, false);
    }

    private Outcome replay(String owner, String scope, String key, String hash) {
        List<String[]> rows = jdbc.query(
                "select payload_hash, response_json from idempotency_record where owner = ? and scope = ? and idempotency_key = ?",
                (rs, i) -> new String[]{rs.getString(1), rs.getString(2)}, owner, scope, key);
        if (rows.isEmpty()) {
            // Bản ghi vừa bị dọn giữa hai câu lệnh: coi như yêu cầu mới sẽ được thử lại.
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Yêu cầu đang được xử lý, vui lòng thử lại");
        }
        if (!rows.get(0)[0].equals(hash)) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT,
                    "Idempotency-Key này đã được dùng cho một yêu cầu khác. Hãy tạo key mới cho yêu cầu mới");
        }
        String stored = rows.get(0)[1];
        try {
            return new Outcome(stored == null ? null : objectMapper.readTree(stored), true);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không đọc được kết quả đã lưu của Idempotency-Key", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không chuyển được dữ liệu yêu cầu/kết quả sang JSON", exception);
        }
    }

    public static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Dọn key cũ (giữ 3 ngày như job Excel). */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 120_000L)
    @Transactional
    public void purgeExpired() {
        jdbc.update("delete from idempotency_record where created_at < now() - interval '3 days'");
    }

}
