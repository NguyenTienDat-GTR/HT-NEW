package phucuong.ht.modules.parish.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.common.validation.OrganizationUnitValidationHelper;
import phucuong.ht.modules.parish.model.request.ParishCreateRequest;
import phucuong.ht.modules.parish.model.request.ParishUpdateRequest;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ParishValidationHelper {

    private final CommonValidationHelper commonValidationHelper;
    private final OrganizationUnitValidationHelper organizationUnitValidationHelper;

    public void validateParishId(UUID id) {
        commonValidationHelper.requireId(id, "ID giáo xứ là bắt buộc");
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
}
