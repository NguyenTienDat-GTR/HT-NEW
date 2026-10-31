package phucuong.ht.modules.diocese.helper;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.common.validation.OrganizationUnitValidationHelper;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.model.request.DioceseCreateRequest;
import phucuong.ht.modules.diocese.model.request.DioceseUpdateRequest;
import phucuong.ht.modules.diocese.repository.DioceseRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DioceseValidationHelperTest {

    private final DioceseRepository dioceseRepository = mock(DioceseRepository.class);
    private final securityUtils securityUtils = mock(securityUtils.class);
    private final CommonValidationHelper commonValidationHelper = new CommonValidationHelper(securityUtils);
    private final OrganizationUnitValidationHelper organizationUnitValidationHelper = new OrganizationUnitValidationHelper(commonValidationHelper);
    private final DioceseValidationHelper helper = new DioceseValidationHelper(
            dioceseRepository,
            commonValidationHelper,
            organizationUnitValidationHelper
    );

    @Test
    void requireExistingDioceseShouldReturnEntity() {
        UUID id = UUID.randomUUID();
        Diocese diocese = new Diocese();
        diocese.setId(id);
        when(dioceseRepository.findById(id)).thenReturn(Optional.of(diocese));

        assertSame(diocese, helper.requireExistingDiocese(id));
    }

    @Test
    void requireExistingDioceseShouldThrowWhenMissing() {
        UUID id = UUID.randomUUID();
        when(dioceseRepository.findById(id)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> helper.requireExistingDiocese(id));

        assertEquals("Không tìm thấy giáo phận: " + id, exception.getMessage());
    }

    @Test
    void validateCreateNameUniquenessShouldRejectDuplicateName() {
        when(dioceseRepository.existsByName("Phú Cường")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> helper.validateCreateNameUniqueness("Phú Cường"));

        assertEquals("Tên giáo phận đã tồn tại", exception.getMessage());
    }

    @Test
    void validateUpdateNameUniquenessShouldRejectDuplicateChangedName() {
        UUID id = UUID.randomUUID();
        when(dioceseRepository.existsByNameAndIdNot("Phú Cường", id)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> helper.validateUpdateNameUniqueness(id, "Phú Cường", "Tên cũ"));

        assertEquals("Tên giáo phận đã tồn tại", exception.getMessage());
    }

    @Test
    void validateCreateInputShouldNormalizeOptionalFields() {
        DioceseCreateRequest request = new DioceseCreateRequest();
        request.setName("  Phú Cường  ");
        request.setUnionName("  Hiệp đoàn  ");
        request.setPhoneNumber("");
        request.setEmail("  admin@example.com  ");

        helper.validateCreateInput(request);

        assertEquals("Phú Cường", request.getName());
        assertEquals("Hiệp đoàn", request.getUnionName());
        assertNull(request.getPhoneNumber());
        assertEquals("admin@example.com", request.getEmail());
    }

    @Test
    void getRequiredAuditActorShouldReturnCurrentActor() {
        Authentication authentication = mock(Authentication.class);
        when(securityUtils.currentAuditActor(authentication)).thenReturn("LEADER1");

        assertEquals("LEADER1", helper.getRequiredAuditActor(authentication));
    }

    @Test
    void validateUpdateInputShouldNormalizeBlankOptionalFields() {
        DioceseUpdateRequest request = new DioceseUpdateRequest();
        request.setName("  Tên mới  ");
        request.setUnionName(" ");

        helper.validateUpdateInput(request);

        assertEquals("Tên mới", request.getName());
        assertNull(request.getUnionName());
    }
}
