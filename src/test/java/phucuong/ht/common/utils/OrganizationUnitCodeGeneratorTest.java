package phucuong.ht.common.utils;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.auth.exception.ApiException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrganizationUnitCodeGeneratorTest {

    private final OrganizationUnitCodeGenerator generator = new OrganizationUnitCodeGenerator();

    @Test
    void shouldGenerateDioceseSlugFromVietnameseUnionName() {
        String code = generator.generateDioceseCode("Phu Cuong", existing -> false);

        assertEquals("phu-cuong", code);
    }

    @Test
    void shouldAppendSuffixWhenDioceseCodeAlreadyExists() {
        Set<String> existing = Set.of("phu-cuong");

        String code = generator.generateDioceseCode("Phu Cuong", existing::contains);

        assertEquals("phu-cuong-2", code);
    }

    @Test
    void shouldNamespaceDeaneryCodeByDiocese() {
        String code = generator.generateDeaneryCode("phu-cuong", "Hiep Hoi 1", existing -> false);

        assertEquals("phu-cuong-hiep-hoi-1", code);
    }

    @Test
    void shouldKeepDeaneryCodesUniqueAcrossDifferentDioceses() {
        String code = generator.generateDeaneryCode("vinh-long", "Hiep Hoi", existing -> false);

        assertEquals("vinh-long-hiep-hoi", code);
    }

    @Test
    void shouldAppendSuffixForScopedCodeWhenCandidateAlreadyExists() {
        Set<String> existing = Set.of("phu-cuong-giao-xu-rat-rat-dai");

        String code = generator.generateParishCode("phu-cuong", "Giao Xu Rat Rat Dai", existing::contains);

        assertEquals("phu-cuong-giao-xu-rat-rat-dai-2", code);
    }

    @Test
    void shouldRejectBlankSourceName() {
        ApiException exception = assertThrows(ApiException.class, () -> generator.generateDioceseCode(" ", existing -> false));

        assertEquals("Source name is required to generate code", exception.getMessage());
    }
}
