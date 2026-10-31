package phucuong.ht.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketOriginPolicyTest {

    @Test
    void exactOriginsAndSubdomainPatternsAreAllowed() {
        assertFalse(WebSocketConfig.isUnsafeOrigin("https://clead.io.vn"));
        assertFalse(WebSocketConfig.isUnsafeOrigin("http://localhost:3000"));
        assertFalse(WebSocketConfig.isUnsafeOrigin("https://*.clead.io.vn"));
    }

    @Test
    void anyOriginOrTooBroadPatternsAreRejected() {
        assertTrue(WebSocketConfig.isUnsafeOrigin("*"));
        assertTrue(WebSocketConfig.isUnsafeOrigin("https://*"));
        assertTrue(WebSocketConfig.isUnsafeOrigin("https://*."));
        assertTrue(WebSocketConfig.isUnsafeOrigin("https://*.com"));
        assertTrue(WebSocketConfig.isUnsafeOrigin("https://evil*.clead.io.vn"));
        assertTrue(WebSocketConfig.isUnsafeOrigin("*.clead.io.vn"));
    }
}
