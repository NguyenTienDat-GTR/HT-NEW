package phucuong.ht.config;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RedisRateLimitService {

    private static final String REJECTED_KEY_PREFIX = "monitoring:rate-limited:";
    // Dài hơn thời gian lưu nhật ký hệ thống (10 ngày) để trang giám sát xem được cả khoảng dài nhất.
    private static final Duration REJECTED_TTL = Duration.ofDays(11);
    private static final long SECONDS_PER_HOUR = 3600;

    private final StringRedisTemplate stringRedisTemplate;

    // Đếm request bị chặn theo từng giờ: các request này dừng trước bộ lọc nhật ký nên không có trong system_log.
    public void recordRejected(Instant at) {
        String key = REJECTED_KEY_PREFIX + at.getEpochSecond() / SECONDS_PER_HOUR;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, REJECTED_TTL);
        }
    }

    public long countRejected(Instant from, Instant to) {
        List<String> keys = new ArrayList<>();
        for (long hour = from.getEpochSecond() / SECONDS_PER_HOUR; hour <= to.getEpochSecond() / SECONDS_PER_HOUR; hour++) {
            keys.add(REJECTED_KEY_PREFIX + hour);
        }
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        if (values == null) {
            return 0;
        }
        return values.stream().filter(Objects::nonNull).mapToLong(Long::parseLong).sum();
    }

    public RateLimitDecision evaluate(String key, int limit, int windowSeconds) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        Long ttl = stringRedisTemplate.getExpire(key);
        long retryAfterSeconds = ttl == null || ttl < 0 ? windowSeconds : ttl;
        boolean allowed = Objects.requireNonNullElse(count, 0L) <= limit;
        return new RateLimitDecision(allowed, Objects.requireNonNullElse(count, 0L), retryAfterSeconds);
    }

    // Đọc số lần đã ghi nhận mà không tăng bộ đếm.
    public RateLimitDecision peek(String key, int limit, int windowSeconds) {
        String raw = stringRedisTemplate.opsForValue().get(key);
        long count = raw == null ? 0L : Long.parseLong(raw);
        Long ttl = stringRedisTemplate.getExpire(key);
        long retryAfterSeconds = ttl == null || ttl < 0 ? windowSeconds : ttl;
        return new RateLimitDecision(count < limit, count, retryAfterSeconds);
    }

    // Ghi nhận thêm một lần (dùng cho đăng nhập thất bại).
    public void record(String key, int windowSeconds) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }
    }

    public record RateLimitDecision(boolean allowed, long currentCount, long retryAfterSeconds) {
    }
}
