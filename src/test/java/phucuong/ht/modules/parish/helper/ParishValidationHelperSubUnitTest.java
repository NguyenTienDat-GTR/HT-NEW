package phucuong.ht.modules.parish.helper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.common.validation.OrganizationUnitValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.model.enums.ParishUnitType;
import phucuong.ht.modules.parish.model.request.ParishCreateRequest;
import phucuong.ht.modules.parish.repository.ParishRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParishValidationHelperSubUnitTest {

    @Mock
    private ParishRepository parishRepository;
    @Mock
    private DeaneryRepository deaneryRepository;
    @Mock
    private OrganizationUnitValidationHelper organizationUnitValidationHelper;

    private ParishValidationHelper helper;

    @BeforeEach
    void setUp() {
        helper = new ParishValidationHelper(parishRepository, deaneryRepository, new CommonValidationHelper(new phucuong.ht.common.utils.securityUtils()), organizationUnitValidationHelper);
    }

    @Test
    void parishHasNoParentAndRejectsParentId() {
        ParishCreateRequest request = new ParishCreateRequest();
        assertNull(helper.resolveParent(request, ParishUnitType.PARISH, UUID.randomUUID()));

        request.setParentParishId(UUID.randomUUID());
        ApiException ex = assertThrows(ApiException.class, () -> helper.resolveParent(request, ParishUnitType.PARISH, UUID.randomUUID()));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void subUnitRequiresParentParishInSameDeanery() {
        UUID deaneryId = UUID.randomUUID();
        Parish parent = parish(UUID.randomUUID(), deaneryId, ParishUnitType.PARISH);
        ParishCreateRequest request = new ParishCreateRequest();
        request.setParentParishId(parent.getId());
        when(parishRepository.findByIdWithDeaneryAndDiocese(parent.getId())).thenReturn(Optional.of(parent));

        assertSame(parent, helper.resolveParent(request, ParishUnitType.GIAO_HO, deaneryId));
        assertThrows(ApiException.class, () -> helper.resolveParent(request, ParishUnitType.GIAO_DIEM, UUID.randomUUID()));
    }

    @Test
    void subUnitCannotMissParentOrNestUnderAnotherSubUnit() {
        UUID deaneryId = UUID.randomUUID();
        ParishCreateRequest noParent = new ParishCreateRequest();
        assertThrows(ApiException.class, () -> helper.resolveParent(noParent, ParishUnitType.GIAO_HO, deaneryId));

        Parish giaoHo = parish(UUID.randomUUID(), deaneryId, ParishUnitType.GIAO_HO);
        ParishCreateRequest nested = new ParishCreateRequest();
        nested.setParentParishId(giaoHo.getId());
        when(parishRepository.findByIdWithDeaneryAndDiocese(giaoHo.getId())).thenReturn(Optional.of(giaoHo));
        assertThrows(ApiException.class, () -> helper.resolveParent(nested, ParishUnitType.GIAO_DIEM, deaneryId));
    }

    private static Parish parish(UUID id, UUID deaneryId, ParishUnitType type) {
        Deanery deanery = new Deanery();
        deanery.setId(deaneryId);
        Parish parish = new Parish();
        parish.setId(id);
        parish.setDeanery(deanery);
        parish.setUnitType(type);
        return parish;
    }
}
