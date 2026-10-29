package phucuong.ht.modules.media.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.media.config.MediaProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;

/**
 * Ký và kiểm tra URL ảnh: {@code /api/media/{assetId}/{variant}?exp={epochSec}&sig={HMAC-SHA256}}.
 * Thời điểm hết hạn được làm tròn lên theo "bucket" nên mọi lần gọi trong cùng một bucket cho ra cùng một URL,
 * trình duyệt và React Query dùng lại được cache.
 */
@Component
public class MediaUrlSigner {

    private final MediaProperties properties;
    private final byte[] key;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public MediaUrlSigner(MediaProperties properties, @Value("${app.security.jwt.access-secret:}") String jwtSecret) {
        this(properties, jwtSecret, Clock.systemUTC());
    }

    MediaUrlSigner(MediaProperties properties, String jwtSecret, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        String secret = properties.getSigningSecret();
        if (secret == null || secret.isBlank()) {
            if (jwtSecret == null || jwtSecret.isBlank()) {
                throw new IllegalStateException("Thiếu app.media.signing-secret (MEDIA_SIGNING_SECRET)");
            }
            secret = "media-url:" + jwtSecret;
        }
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public SignedParams sign(UUID assetId, String variant) {
        long now = clock.instant().getEpochSecond();
        long bucket = Math.max(1, properties.getUrlBucket().toSeconds());
        long ttl = Math.max(bucket, properties.getUrlTtl().toSeconds());
        long exp = ((now + ttl + bucket - 1) / bucket) * bucket;
        if (exp - now > ttl) {
            exp -= bucket;
        }
        return new SignedParams(exp, signature(assetId, variant, exp));
    }

    public String url(UUID assetId, String variant) {
        SignedParams params = sign(assetId, variant);
        return properties.getUrlPath() + "/" + assetId + "/" + variant + "?exp=" + params.exp() + "&sig=" + params.sig();
    }

    /** @return true nếu chữ ký đúng và chưa hết hạn. */
    public boolean verify(UUID assetId, String variant, long exp, String sig) {
        if (sig == null || exp < clock.instant().getEpochSecond()) {
            return false;
        }
        byte[] expected = signature(assetId, variant, exp).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.UTF_8));
    }

    /** Số giây còn hiệu lực của URL (dùng cho Cache-Control). */
    public long secondsLeft(long exp) {
        return Math.max(0, exp - clock.instant().getEpochSecond());
    }

    private String signature(UUID assetId, String variant, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal((assetId + "|" + variant + "|" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public record SignedParams(long exp, String sig) {
    }
}
