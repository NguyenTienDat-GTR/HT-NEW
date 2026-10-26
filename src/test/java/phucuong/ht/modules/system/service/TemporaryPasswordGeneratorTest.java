package phucuong.ht.modules.system.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporaryPasswordGeneratorTest {

    @Test
    void shouldGenerateEightLettersWithUpperAndLowerCaseAndNoDigitsOrSymbols() {
        for (int index = 0; index < 2_000; index++) {
            String password = TemporaryPasswordGenerator.generate();

            assertEquals(8, password.length());
            assertTrue(password.matches("[A-Za-z]{8}"), "Chỉ gồm chữ cái: " + password);
            assertTrue(password.chars().anyMatch(Character::isUpperCase), "Phải có chữ hoa: " + password);
            assertTrue(password.chars().anyMatch(Character::isLowerCase), "Phải có chữ thường: " + password);
        }
    }

    @Test
    void shouldExcludeConfusingCharacters() {
        for (int index = 0; index < 2_000; index++) {
            String password = TemporaryPasswordGenerator.generate();

            assertFalse(password.matches(".*[IlOo].*"), "Không chứa I, l, O, o: " + password);
        }
    }

    @Test
    void shouldNotRepeatTheSamePasswordEveryTime() {
        Set<String> passwords = new HashSet<>();
        for (int index = 0; index < 200; index++) {
            passwords.add(TemporaryPasswordGenerator.generate());
        }

        assertTrue(passwords.size() > 190, "Mật khẩu phải ngẫu nhiên, thực tế: " + passwords.size());
    }
}
