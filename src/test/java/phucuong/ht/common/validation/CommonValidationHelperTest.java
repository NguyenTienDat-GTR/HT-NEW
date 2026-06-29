package phucuong.ht.common.validation;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.exception.ApiException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommonValidationHelperTest {

    private final securityUtils securityUtils = mock(securityUtils.class);
    private final CommonValidationHelper helper = new CommonValidationHelper(securityUtils);

    @Test
    void requireIdShouldRejectNull() {
        ApiException exception = assertThrows(ApiException.class, () -> helper.requireId(null, "ID là bắt buộc"));

        assertEquals("ID là bắt buộc", exception.getMessage());
    }

    @Test
    void requireIdShouldAcceptUuid() {
        helper.requireId(UUID.randomUUID(), "ID là bắt buộc");
    }

    @Test
    void requireAuditActorShouldRejectBlankActor() {
        Authentication authentication = mock(Authentication.class);
        when(securityUtils.currentAuditActor(authentication)).thenReturn(" ");

        ApiException exception = assertThrows(
                ApiException.class,
                () -> helper.requireAuditActor(authentication, "Thiếu người thực hiện")
        );

        assertEquals("Thiếu người thực hiện", exception.getMessage());
    }

    @Test
    void isChangedShouldCompareOnlyTextValues() {
        assertTrue(helper.isChanged("New", "Old"));
    }

    @Test
    void normalizeOptionalTextShouldNullBlankValues() {
        assertNull(helper.normalizeOptionalText(" "));
    }

    @Test
    void normalizeRequiredTextShouldTrimValue() {
        assertEquals("Giáo phận", helper.normalizeRequiredText("  Giáo phận  "));
    }
}
