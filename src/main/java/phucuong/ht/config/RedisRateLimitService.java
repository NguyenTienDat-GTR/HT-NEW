package phucuong.ht.config;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RedisRateLimitService {

    private final StringRedisTemplate stringRedisTemplate;

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
