package phucuong.ht.modules.diocese.helper;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import phucuong.ht.common.exception.ApiException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SiteSubdomainRulesTest {

    @Test
    void normalizesCaseAndWhitespace() {
        assertEquals("phucuong", SiteSubdomainRules.normalize("  PhuCuong "));
        assertEquals("gp-sai-gon2", SiteSubdomainRules.normalize("gp-sai-gon2"));
    }

    @Test
    void rejectsInvalidLabels() {
        for (String bad : new String[]{null, "", "  ", "ab", "-abc", "abc-", "ab_c", "a.b", "phú-cường", "a".repeat(64), "abc def", "abc/de"}) {
            ApiException exception = assertThrows(ApiException.class, () -> SiteSubdomainRules.normalize(bad), String.valueOf(bad));
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        }
    }

    @Test
    void rejectsReservedLabels() {
        for (String reserved : new String[]{"test", "www", "staging", "api", "mail", "admin", "TEST"}) {
            ApiException exception = assertThrows(ApiException.class, () -> SiteSubdomainRules.normalize(reserved), reserved);
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        }
    }
}
