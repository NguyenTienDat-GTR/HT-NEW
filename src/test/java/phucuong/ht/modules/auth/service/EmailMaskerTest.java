package phucuong.ht.modules.auth.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailMaskerTest {

    @Test
    void shouldMaskLocalPartAndDomainHostButKeepSuffix() {
        assertEquals("ng***@g***.com", EmailMasker.mask("nguyenvan@gmail.com"));
        assertEquals("ad***@p***.vn", EmailMasker.mask("admin@phucuong.org.vn"));
    }

    @Test
    void shouldKeepOnlyFirstCharacterWhenLocalPartIsVeryShort() {
        assertEquals("a***@g***.com", EmailMasker.mask("a@gmail.com"));
        assertEquals("a***@g***.com", EmailMasker.mask("ab@gmail.com"));
    }

    @Test
    void shouldNeverRevealMoreThanTwoLocalCharactersOrOneHostCharacter() {
        String masked = EmailMasker.mask("congcuongnguyen@example.vn");

        assertEquals("co***@e***.vn", masked);
    }

    @Test
    void shouldHandleMalformedAndBlankValuesWithoutLeaking() {
        assertEquals("", EmailMasker.mask(null));
        assertEquals("", EmailMasker.mask("   "));
        assertEquals("***", EmailMasker.mask("no-at-sign"));
        assertEquals("***", EmailMasker.mask("@gmail.com"));
        assertEquals("***", EmailMasker.mask("user@"));
    }

    @Test
    void shouldTrimSurroundingWhitespace() {
        assertEquals("ng***@g***.com", EmailMasker.mask("  nguyenvan@gmail.com "));
    }
}
