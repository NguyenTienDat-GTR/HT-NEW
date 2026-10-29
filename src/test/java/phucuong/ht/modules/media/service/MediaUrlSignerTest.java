package phucuong.ht.modules.media.service;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.media.config.MediaProperties;

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
        return properties;
    }

    @Test
    void validSignatureVerifies() {
        MediaUrlSigner signer = new MediaUrlSigner(properties(), "");
        assertTrue(signer.verify(assetId, "thumb", signer.sign(assetId, "thumb")));
    }

    @Test
    void tamperedSignatureOrParametersAreRejected() {
        MediaUrlSigner signer = new MediaUrlSigner(properties(), "");
        String sig = signer.sign(assetId, "thumb");
        assertFalse(signer.verify(assetId, "medium", sig), "đổi biến thể");
        assertFalse(signer.verify(UUID.randomUUID(), "thumb", sig), "đổi asset");
        assertFalse(signer.verify(assetId, "thumb", sig + "x"), "sửa chữ ký");
        assertFalse(signer.verify(assetId, "thumb", null), "thiếu chữ ký");
    }

    @Test
    void urlNeverExpiresAndIsAlwaysTheSame() {
        MediaUrlSigner signer = new MediaUrlSigner(properties(), "");
        String url = signer.url(assetId, "thumb");
        assertTrue(url.startsWith("/api/media/" + assetId + "/thumb?sig="));
        assertFalse(url.contains("exp="));
        assertEquals(url, new MediaUrlSigner(properties(), "").url(assetId, "thumb"));
    }

    @Test
    void differentSecretsDoNotVerifyEachOther() {
        MediaProperties other = properties();
        other.setSigningSecret("another-secret");
        MediaUrlSigner a = new MediaUrlSigner(properties(), "");
        MediaUrlSigner b = new MediaUrlSigner(other, "");
        assertFalse(b.verify(assetId, "thumb", a.sign(assetId, "thumb")));
    }

    @Test
    void fallsBackToJwtSecretAndFailsWhenNothingConfigured() {
        MediaProperties noSecret = properties();
        noSecret.setSigningSecret(null);
        MediaUrlSigner derived = new MediaUrlSigner(noSecret, "jwt-secret");
        assertTrue(derived.verify(assetId, "thumb", derived.sign(assetId, "thumb")));
        assertThrows(IllegalStateException.class, () -> new MediaUrlSigner(noSecret, ""));
    }
}
