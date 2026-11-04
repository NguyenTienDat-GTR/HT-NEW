package phucuong.ht.modules.certificate.config.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificateLayoutItemTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void hiddenItemsAreNotPrintedButLockedOnesAre() throws Exception {
        CertificateLayoutItem hidden = mapper.readValue("{\"type\":\"TEXT\",\"text\":\"a\",\"hidden\":true}", CertificateLayoutItem.class);
        CertificateLayoutItem locked = mapper.readValue("{\"type\":\"TEXT\",\"text\":\"b\",\"locked\":true}", CertificateLayoutItem.class);
        CertificateLayoutItem plain = mapper.readValue("{\"type\":\"TEXT\",\"text\":\"c\"}", CertificateLayoutItem.class);
        assertFalse(hidden.isPrinted());
        assertTrue(locked.isPrinted());
        assertTrue(plain.isPrinted());
        assertEquals(Boolean.TRUE, locked.getLocked());
    }

    @Test
    void lockAndHiddenFlagsSurviveASaveRoundTrip() throws Exception {
        CertificateLayoutItem item = mapper.readValue("{\"type\":\"FIELD\",\"fieldKey\":\"courseName\",\"locked\":true,\"hidden\":true}", CertificateLayoutItem.class);
        CertificateLayoutItem copy = mapper.readValue(mapper.writeValueAsString(item), CertificateLayoutItem.class);
        assertEquals(Boolean.TRUE, copy.getLocked());
        assertEquals(Boolean.TRUE, copy.getHidden());
    }
}
