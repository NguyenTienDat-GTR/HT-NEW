package phucuong.ht.modules.deanery.helper;

import org.junit.jupiter.api.Test;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.common.validation.OrganizationUnitValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.model.request.DeaneryCreateRequest;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.repository.DioceseRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeaneryValidationHelperTest {

    private final DeaneryRepository deaneryRepository = mock(DeaneryRepository.class);
    private final DioceseRepository dioceseRepository = mock(DioceseRepository.class);
    private final CommonValidationHelper commonValidationHelper = new CommonValidationHelper(mock(securityUtils.class));
    private final OrganizationUnitValidationHelper organizationUnitValidationHelper = new OrganizationUnitValidationHelper(commonValidationHelper);
    private final DeaneryValidationHelper helper = new DeaneryValidationHelper(
            deaneryRepository,
            dioceseRepository,
            commonValidationHelper,
            organizationUnitValidationHelper
    );

    @Test
    void requireExistingDeaneryWithDioceseShouldReturnEntity() {
        UUID id = UUID.randomUUID();
        Deanery deanery = new Deanery();
        deanery.setId(id);
        when(deaneryRepository.findByIdWithDiocese(id)).thenReturn(Optional.of(deanery));

        assertSame(deanery, helper.requireExistingDeaneryWithDiocese(id));
    }

    @Test
    void requireExistingDioceseShouldThrowWhenMissing() {
        UUID id = UUID.randomUUID();
        when(dioceseRepository.findById(id)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> helper.requireExistingDiocese(id));

        assertEquals("Không tìm thấy giáo phận: " + id, exception.getMessage());
    }

    @Test
    void validateCreateNameUniquenessShouldRejectDuplicateNameInsideDiocese() {
        UUID dioceseId = UUID.randomUUID();
        when(deaneryRepository.existsByNameAndDioceseId("Thủ Dầu Một", dioceseId)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> helper.validateCreateNameUniqueness("Thủ Dầu Một", dioceseId));

        assertEquals("Tên giáo hạt đã tồn tại trong giáo phận", exception.getMessage());
    }

    @Test
    void validateUpdateNameUniquenessShouldRejectDuplicateChangedNameInsideDiocese() {
        UUID deaneryId = UUID.randomUUID();
        UUID dioceseId = UUID.randomUUID();
        Deanery existingDeanery = new Deanery();
        existingDeanery.setName("Tên cũ");
        existingDeanery.setDiocese(diocese(dioceseId));
        when(deaneryRepository.existsByNameAndDioceseIdAndIdNot("Thủ Dầu Một", dioceseId, deaneryId)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> helper.validateUpdateNameUniqueness(deaneryId, "Thủ Dầu Một", existingDeanery));

        assertEquals("Tên giáo hạt đã tồn tại trong giáo phận", exception.getMessage());
    }

    @Test
    void validateCreateInputShouldNormalizeTextFields() {
        DeaneryCreateRequest request = new DeaneryCreateRequest();
        request.setName("  Thủ Dầu Một  ");
        request.setAssociationName("  Hiệp đoàn  ");
        request.setPhoneNumber("");

        helper.validateCreateInput(request);

        assertEquals("Thủ Dầu Một", request.getName());
        assertEquals("Hiệp đoàn", request.getAssociationName());
        assertNull(request.getPhoneNumber());
    }

    private Diocese diocese(UUID dioceseId) {
        Diocese diocese = new Diocese();
        diocese.setId(dioceseId);
        return diocese;
    }
}
