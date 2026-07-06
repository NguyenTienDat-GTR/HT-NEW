package phucuong.ht.modules.parish.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.common.validation.OrganizationUnitValidationHelper;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.model.request.ParishCreateRequest;
import phucuong.ht.modules.parish.model.request.ParishUpdateRequest;
import phucuong.ht.modules.parish.repository.ParishRepository;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ParishValidationHelper {

    private final ParishRepository parishRepository;
    private final DeaneryRepository deaneryRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final OrganizationUnitValidationHelper organizationUnitValidationHelper;

    public void validateParishId(UUID id) {
        commonValidationHelper.requireId(id, "ID giáo xứ là bắt buộc");
    }

    public Parish requireExistingParish(UUID id) {
        validateParishId(id);
        return parishRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo xứ: " + id));
    }

    public Parish requireExistingParishWithDeaneryAndDiocese(UUID id) {
        validateParishId(id);
        return parishRepository.findByIdWithDeaneryAndDiocese(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo xứ: " + id));
    }

    public Deanery requireExistingDeanery(UUID deaneryId) {
        commonValidationHelper.requireId(deaneryId, "ID giáo hạt là bắt buộc");
        return deaneryRepository.findById(deaneryId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo hạt: " + deaneryId));
    }

    public Deanery requireExistingDeaneryWithDiocese(UUID deaneryId) {
        commonValidationHelper.requireId(deaneryId, "ID giáo hạt là bắt buộc");
        return deaneryRepository.findByIdWithDiocese(deaneryId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo hạt: " + deaneryId));
    }

    public void validateCreateInput(ParishCreateRequest request) {
        organizationUnitValidationHelper.normalizeCreateInput(request);
        if (request != null) {
            request.setChapterName(commonValidationHelper.normalizeRequiredText(request.getChapterName()));
        }
    }

    public void validateUpdateInput(ParishUpdateRequest request) {
        organizationUnitValidationHelper.normalizeUpdateInput(request);
        if (request != null) {
            request.setChapterName(commonValidationHelper.normalizeOptionalText(request.getChapterName()));
        }
    }

    public void validateCreateNameUniqueness(String name, UUID deaneryId) {
        if (parishRepository.existsByNameAndDeaneryId(name, deaneryId)) {
            throw commonValidationHelper.conflict("Tên giáo xứ đã tồn tại trong giáo hạt");
        }
    }

    public void validateUpdateNameUniqueness(UUID id, String requestedName, Parish existingParish) {
        if (!commonValidationHelper.isChanged(requestedName, existingParish.getName())) {
            return;
        }

        if (parishRepository.existsByNameAndDeaneryIdAndIdNot(
                requestedName,
                existingParish.getDeanery().getId(),
                id
        )) {
            throw commonValidationHelper.conflict("Tên giáo xứ đã tồn tại trong giáo hạt");
        }
    }

    public String getRequiredAuditActor(Authentication authentication) {
        return commonValidationHelper.requireAuditActor(
                authentication,
                "Tài khoản hiện tại không có thông tin người thực hiện để ghi nhận lịch sử"
        );
    }

    public void validateToggleConfirmation(String confirmationText) {
        commonValidationHelper.requireStatusToggleConfirmation(confirmationText);
    }
}
