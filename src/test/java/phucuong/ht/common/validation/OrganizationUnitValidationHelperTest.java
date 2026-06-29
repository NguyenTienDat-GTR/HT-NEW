package phucuong.ht.common.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import phucuong.ht.common.model.request.OrganizationUnitCreateRequest;
import phucuong.ht.common.model.request.OrganizationUnitUpdateRequest;
import phucuong.ht.common.utils.securityUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OrganizationUnitValidationHelperTest {

    private final CommonValidationHelper commonValidationHelper = new CommonValidationHelper(mock(securityUtils.class));
    private final OrganizationUnitValidationHelper helper = new OrganizationUnitValidationHelper(commonValidationHelper);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void normalizeCreateInputShouldTrimTextAndNullBlankOptionalFields() {
        OrganizationUnitCreateRequest request = new OrganizationUnitCreateRequest();
        request.setName("  Giáo phận  ");
        request.setChaplain(" ");
        request.setPhoneNumber("");
        request.setEmail("  admin@example.com  ");

        helper.normalizeCreateInput(request);

        assertEquals("Giáo phận", request.getName());
        assertNull(request.getChaplain());
        assertNull(request.getPhoneNumber());
        assertEquals("admin@example.com", request.getEmail());
    }

    @Test
    void normalizeUpdateInputShouldTrimNameAndNullBlankOptionalFields() {
        OrganizationUnitUpdateRequest request = new OrganizationUnitUpdateRequest();
        request.setName("  Tên mới  ");
        request.setChaplain("");
        request.setPhoneNumber(" ");
        request.setEmail("  admin@example.com  ");

        helper.normalizeUpdateInput(request);

        assertEquals("Tên mới", request.getName());
        assertNull(request.getChaplain());
        assertNull(request.getPhoneNumber());
        assertEquals("admin@example.com", request.getEmail());
    }

    @Test
    void phoneNumberShouldAcceptVietnameseTenDigitNumberStartingWithZero() {
        OrganizationUnitCreateRequest request = new OrganizationUnitCreateRequest();
        request.setName("Giáo phận");
        request.setPhoneNumber("0912345678");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void phoneNumberShouldRejectInvalidVietnameseNumbers() {
        for (String phoneNumber : new String[]{"912345678", "0012345678", "091234567", "09123456789", "09A2345678"}) {
            OrganizationUnitUpdateRequest request = new OrganizationUnitUpdateRequest();
            request.setPhoneNumber(phoneNumber);

            assertEquals(1, validator.validate(request).size(), phoneNumber);
        }
    }
}
