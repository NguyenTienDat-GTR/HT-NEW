package phucuong.ht.modules.media.service;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.media.config.MediaProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaUrlSignerTest {

    private final UUID assetId = UUID.randomUUID();

    private MediaProperties properties() {
        MediaProperties properties = new MediaProperties();
        properties.setSigningSecret("unit-test-secret");
        properties.setUrlTtl(Duration.ofMinutes(30));
        properties.setUrlBucket(Duration.ofMinutes(10));
        return properties;
    }

    private Clock at(long epochSecond) {
        return Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC);
    }

    @Test
    void validSignatureVerifies() {
        MediaUrlSigner signer = new MediaUrlSigner(properties(), "", at(1_000_000));
        MediaUrlSigner.SignedParams params = signer.sign(assetId, "thumb");
        assertTrue(signer.verify(assetId, "thumb", params.exp(), params.sig()));
    }

    @Test
    void tamperedSignatureOrParametersAreRejected() {
        MediaUrlSigner signer = new MediaUrlSigner(properties(), "", at(1_000_000));
        MediaUrlSigner.SignedParams params = signer.sign(assetId, "thumb");
        assertFalse(signer.verify(assetId, "medium", params.exp(), params.sig()), "đổi biến thể");
        assertFalse(signer.verify(UUID.randomUUID(), "thumb", params.exp(), params.sig()), "đổi asset");
        assertFalse(signer.verify(assetId, "thumb", params.exp() + 600, params.sig()), "kéo dài hạn");
        assertFalse(signer.verify(assetId, "thumb", params.exp(), params.sig() + "x"), "sửa chữ ký");
        assertFalse(signer.verify(assetId, "thumb", params.exp(), null), "thiếu chữ ký");
    }

    @Test
    void expiredUrlIsRejected() {
        MediaUrlSigner issuer = new MediaUrlSigner(properties(), "", at(1_000_000));
        MediaUrlSigner.SignedParams params = issuer.sign(assetId, "thumb");
        MediaUrlSigner later = new MediaUrlSigner(properties(), "", at(params.exp() + 1));
        assertFalse(later.verify(assetId, "thumb", params.exp(), params.sig()));
    }

    @Test
    void urlsAreStableWithinABucketAndLiveBetweenTtlMinusBucketAndTtl() {
        long start = 1_000_000;
        MediaUrlSigner first = new MediaUrlSigner(properties(), "", at(start));
        long exp = first.sign(assetId, "thumb").exp();
        assertTrue(exp - start <= 30 * 60, "không vượt quá TTL");
        assertTrue(exp - start > 20 * 60, "còn hiệu lực ít nhất ttl - bucket");
        // Hai lần gọi gần nhau cho cùng URL (để trình duyệt dùng lại cache).
        MediaUrlSigner second = new MediaUrlSigner(properties(), "", at(start + 30));
        assertEquals(first.url(assetId, "thumb"), second.url(assetId, "thumb"));
    }

    @Test
    void differentSecretsDoNotVerifyEachOther() {
        MediaProperties other = properties();
        other.setSigningSecret("another-secret");
        MediaUrlSigner a = new MediaUrlSigner(properties(), "", at(1_000_000));
        MediaUrlSigner b = new MediaUrlSigner(other, "", at(1_000_000));
        MediaUrlSigner.SignedParams params = a.sign(assetId, "thumb");
        assertFalse(b.verify(assetId, "thumb", params.exp(), params.sig()));
    }

    @Test
    void fallsBackToJwtSecretAndFailsWhenNothingConfigured() {
        MediaProperties noSecret = properties();
        noSecret.setSigningSecret(null);
        MediaUrlSigner derived = new MediaUrlSigner(noSecret, "jwt-secret", at(1_000_000));
        MediaUrlSigner.SignedParams params = derived.sign(assetId, "thumb");
        assertTrue(derived.verify(assetId, "thumb", params.exp(), params.sig()));
        assertThrows(IllegalStateException.class, () -> new MediaUrlSigner(noSecret, "", at(1_000_000)));
    }
}
