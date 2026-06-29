package phucuong.ht.modules.deanery.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.common.validation.OrganizationUnitValidationHelper;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.model.request.DeaneryCreateRequest;
import phucuong.ht.modules.deanery.model.request.DeaneryUpdateRequest;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.repository.DioceseRepository;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeaneryValidationHelper {

    private final DeaneryRepository deaneryRepository;
    private final DioceseRepository dioceseRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final OrganizationUnitValidationHelper organizationUnitValidationHelper;

    public void validateDeaneryId(UUID id) {
        commonValidationHelper.requireId(id, "ID giáo hạt là bắt buộc");
    }

    public Deanery requireExistingDeanery(UUID id) {
        validateDeaneryId(id);
        return deaneryRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo hạt: " + id));
    }

    public Deanery requireExistingDeaneryWithDiocese(UUID id) {
        validateDeaneryId(id);
        return deaneryRepository.findByIdWithDiocese(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo hạt: " + id));
    }

    public Diocese requireExistingDiocese(UUID dioceseId) {
        commonValidationHelper.requireId(dioceseId, "ID giáo phận là bắt buộc");
        return dioceseRepository.findById(dioceseId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo phận: " + dioceseId));
    }

    public void validateCreateInput(DeaneryCreateRequest request) {
        organizationUnitValidationHelper.normalizeCreateInput(request);
        if (request != null) {
            request.setAssociationName(commonValidationHelper.normalizeRequiredText(request.getAssociationName()));
        }
    }

    public void validateUpdateInput(DeaneryUpdateRequest request) {
        organizationUnitValidationHelper.normalizeUpdateInput(request);
        if (request != null) {
            request.setAssociationName(commonValidationHelper.normalizeOptionalText(request.getAssociationName()));
        }
    }

    public void validateCreateNameUniqueness(String name, UUID dioceseId) {
        if (deaneryRepository.existsByNameAndDioceseId(name, dioceseId)) {
            throw commonValidationHelper.conflict("Tên giáo hạt đã tồn tại trong giáo phận");
        }
    }

    public void validateUpdateNameUniqueness(UUID id, String requestedName, Deanery existingDeanery) {
        if (!commonValidationHelper.isChanged(requestedName, existingDeanery.getName())) {
            return;
        }

        if (deaneryRepository.existsByNameAndDioceseIdAndIdNot(
                requestedName,
                existingDeanery.getDiocese().getId(),
                id
        )) {
            throw commonValidationHelper.conflict("Tên giáo hạt đã tồn tại trong giáo phận");
        }
    }

    public String getRequiredAuditActor(Authentication authentication) {
        return commonValidationHelper.requireAuditActor(
                authentication,
                "Tài khoản hiện tại không có thông tin người thực hiện để ghi nhận lịch sử"
        );
    }
}
