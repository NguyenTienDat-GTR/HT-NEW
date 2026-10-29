package phucuong.ht.modules.media.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import phucuong.ht.modules.media.config.MediaProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

/**
 * Ký và kiểm tra URL ảnh: {@code /api/media/{assetId}/{variant}?sig={HMAC-SHA256}}.
 * Chữ ký không có thời hạn (ảnh luôn hiển thị được trong danh sách), nên cùng một ảnh luôn có cùng một URL và
 * trình duyệt dùng lại được cache. Quyền xem do đăng nhập quyết định: người ngoài có link vẫn bị từ chối.
 */
@Component
public class MediaUrlSigner {

    private final MediaProperties properties;
    private final byte[] key;

    @org.springframework.beans.factory.annotation.Autowired
    public MediaUrlSigner(MediaProperties properties, @Value("${app.security.jwt.access-secret:}") String jwtSecret) {
        this.properties = properties;
        String secret = properties.getSigningSecret();
        if (secret == null || secret.isBlank()) {
            if (jwtSecret == null || jwtSecret.isBlank()) {
                throw new IllegalStateException("Thiếu app.media.signing-secret (MEDIA_SIGNING_SECRET)");
            }
            secret = "media-url:" + jwtSecret;
        }
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(UUID assetId, String variant) {
        return signature(assetId, variant);
    }

    public String url(UUID assetId, String variant) {
        return properties.getUrlPath() + "/" + assetId + "/" + variant + "?sig=" + sign(assetId, variant);
    }

    /** @return true nếu chữ ký đúng. */
    public boolean verify(UUID assetId, String variant, String sig) {
        if (sig == null) {
            return false;
        }
        byte[] expected = signature(assetId, variant).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.UTF_8));
    }

    private String signature(UUID assetId, String variant) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal((assetId + "|" + variant).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
