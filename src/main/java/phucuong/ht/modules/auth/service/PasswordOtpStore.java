package phucuong.ht.modules.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * Lưu trạng thái đổi mật khẩu bằng OTP trên Redis. Chỉ lưu hash của OTP và change token,
 * không lưu giá trị gốc.
 */
@Component
@RequiredArgsConstructor
public class PasswordOtpStore {

    private static final String OTP_KEY = "auth:pwd-otp:";
    private static final String COOLDOWN_KEY = "auth:pwd-otp-cooldown:";
    private static final String OLD_PASSWORD_FAIL_KEY = "auth:pwd-old-fail:";
    private static final String CHANGE_TOKEN_KEY = "auth:pwd-change:";
    private static final String FIELD_HASH = "hash";
    private static final String FIELD_ATTEMPTS = "attempts";

    private final StringRedisTemplate redis;

    public void saveOtp(String username, String otp, Duration ttl) {
        String key = OTP_KEY + username;
        redis.delete(key);
        redis.opsForHash().putAll(key, Map.of(FIELD_HASH, hash(username, otp), FIELD_ATTEMPTS, "0"));
        redis.expire(key, ttl);
    }

    /** Trả về null nếu không có OTP (đã hết hạn hoặc chưa gửi). */
    public StoredOtp findOtp(String username) {
        Map<Object, Object> values = redis.opsForHash().entries(OTP_KEY + username);
        if (values == null || values.isEmpty() || values.get(FIELD_HASH) == null) {
            return null;
        }
        int attempts = Integer.parseInt(String.valueOf(values.getOrDefault(FIELD_ATTEMPTS, "0")));
        return new StoredOtp(String.valueOf(values.get(FIELD_HASH)), attempts);
    }

    /** Chụp lại OTP hiện có (kể cả thời hạn còn lại) để khôi phục nếu các bước sau lỗi; null nếu không có. */
    public OtpSnapshot snapshotOtp(String username) {
        String key = OTP_KEY + username;
        Map<Object, Object> values = redis.opsForHash().entries(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        Long ttlMillis = redis.getExpire(key, java.util.concurrent.TimeUnit.MILLISECONDS);
        return new OtpSnapshot(Map.copyOf(values), ttlMillis == null ? -1 : ttlMillis);
    }

    /** Khôi phục OTP đã chụp; không có bản chụp (hoặc đã hết hạn) thì xóa OTP hiện tại. */
    public void restoreOtp(String username, OtpSnapshot snapshot) {
        String key = OTP_KEY + username;
        redis.delete(key);
        if (snapshot == null || snapshot.ttlMillis() <= 0) {
            return;
        }
        redis.opsForHash().putAll(key, snapshot.values());
        redis.expire(key, Duration.ofMillis(snapshot.ttlMillis()));
    }

    public long incrementOtpAttempts(String username) {
        Long attempts = redis.opsForHash().increment(OTP_KEY + username, FIELD_ATTEMPTS, 1);
        return attempts == null ? 0 : attempts;
    }

    public void deleteOtp(String username) {
        redis.delete(OTP_KEY + username);
    }

    public boolean matchesOtp(String username, String otp, StoredOtp stored) {
        return constantTimeEquals(hash(username, otp), stored.hash());
    }

    /** Bắt đầu thời gian chờ gửi lại OTP. Trả về false nếu vẫn đang trong thời gian chờ. */
    public boolean tryStartCooldown(String username, Duration cooldown) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(COOLDOWN_KEY + username, "1", cooldown));
    }

    public long cooldownRemainingSeconds(String username) {
        Long ttl = redis.getExpire(COOLDOWN_KEY + username);
        return ttl == null || ttl < 0 ? 0 : ttl;
    }

    public void clearCooldown(String username) {
        redis.delete(COOLDOWN_KEY + username);
    }

    public long oldPasswordFailures(String username) {
        String value = redis.opsForValue().get(OLD_PASSWORD_FAIL_KEY + username);
        return value == null ? 0 : Long.parseLong(value);
    }

    public void recordOldPasswordFailure(String username, Duration window) {
        String key = OLD_PASSWORD_FAIL_KEY + username;
        Long failures = redis.opsForValue().increment(key);
        if (failures != null && failures == 1) {
            redis.expire(key, window);
        }
    }

    public void clearOldPasswordFailures(String username) {
        redis.delete(OLD_PASSWORD_FAIL_KEY + username);
    }

    public void saveChangeToken(String username, String token, Duration ttl) {
        redis.opsForValue().set(CHANGE_TOKEN_KEY + username, hash(username, token), ttl);
    }

    public boolean matchesChangeToken(String username, String token) {
        String stored = redis.opsForValue().get(CHANGE_TOKEN_KEY + username);
        return stored != null && constantTimeEquals(hash(username, token), stored);
    }

    public void deleteChangeToken(String username) {
        redis.delete(CHANGE_TOKEN_KEY + username);
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private String hash(String username, String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((username + ":" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 không khả dụng", exception);
        }
    }

    public record StoredOtp(String hash, int attempts) {
    }

    public record OtpSnapshot(Map<Object, Object> values, long ttlMillis) {
    }
}
