package phucuong.ht.modules.parish.helper;

import org.junit.jupiter.api.Test;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.common.validation.OrganizationUnitValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.model.request.ParishCreateRequest;
import phucuong.ht.modules.parish.repository.ParishRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParishValidationHelperTest {

    private final ParishRepository parishRepository = mock(ParishRepository.class);
    private final DeaneryRepository deaneryRepository = mock(DeaneryRepository.class);
    private final CommonValidationHelper commonValidationHelper = new CommonValidationHelper(mock(securityUtils.class));
    private final OrganizationUnitValidationHelper organizationUnitValidationHelper = new OrganizationUnitValidationHelper(commonValidationHelper);
    private final ParishValidationHelper helper = new ParishValidationHelper(
            parishRepository,
            deaneryRepository,
            commonValidationHelper,
            organizationUnitValidationHelper
    );

    @Test
    void requireExistingParishWithDeaneryAndDioceseShouldReturnEntity() {
        UUID id = UUID.randomUUID();
        Parish parish = new Parish();
        parish.setId(id);
        when(parishRepository.findByIdWithDeaneryAndDiocese(id)).thenReturn(Optional.of(parish));

        assertSame(parish, helper.requireExistingParishWithDeaneryAndDiocese(id));
    }

    @Test
    void requireExistingDeaneryWithDioceseShouldThrowWhenMissing() {
        UUID id = UUID.randomUUID();
        when(deaneryRepository.findByIdWithDiocese(id)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> helper.requireExistingDeaneryWithDiocese(id));

        assertEquals("Không tìm thấy giáo hạt: " + id, exception.getMessage());
    }

    @Test
    void validateCreateNameUniquenessShouldRejectDuplicateNameInsideDeanery() {
        UUID deaneryId = UUID.randomUUID();
        when(parishRepository.existsByNameAndDeaneryId("Phú Cường", deaneryId)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> helper.validateCreateNameUniqueness("Phú Cường", deaneryId));

        assertEquals("Tên giáo xứ đã tồn tại trong giáo hạt", exception.getMessage());
    }

    @Test
    void validateUpdateNameUniquenessShouldRejectDuplicateChangedNameInsideDeanery() {
        UUID parishId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        Parish existingParish = new Parish();
        existingParish.setName("Tên cũ");
        existingParish.setDeanery(deanery(deaneryId));
        when(parishRepository.existsByNameAndDeaneryIdAndIdNot("Phú Cường", deaneryId, parishId)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> helper.validateUpdateNameUniqueness(parishId, "Phú Cường", existingParish));

        assertEquals("Tên giáo xứ đã tồn tại trong giáo hạt", exception.getMessage());
    }

    @Test
    void validateCreateInputShouldNormalizeTextFields() {
        ParishCreateRequest request = new ParishCreateRequest();
        request.setName("  Phú Cường  ");
        request.setChapterName("  Chapter  ");
        request.setPhoneNumber("");

        helper.validateCreateInput(request);

        assertEquals("Phú Cường", request.getName());
        assertEquals("Chapter", request.getChapterName());
        assertNull(request.getPhoneNumber());
    }

    private Deanery deanery(UUID deaneryId) {
        Deanery deanery = new Deanery();
        deanery.setId(deaneryId);
        Diocese diocese = new Diocese();
        diocese.setId(UUID.randomUUID());
        deanery.setDiocese(diocese);
        return deanery;
    }
}
