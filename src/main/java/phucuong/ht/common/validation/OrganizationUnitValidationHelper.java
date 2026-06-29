package phucuong.ht.common.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import phucuong.ht.common.model.request.OrganizationUnitCreateRequest;
import phucuong.ht.common.model.request.OrganizationUnitUpdateRequest;

@Component
@RequiredArgsConstructor
public class OrganizationUnitValidationHelper {

    private final CommonValidationHelper commonValidationHelper;

    public void normalizeCreateInput(OrganizationUnitCreateRequest request) {
        if (request == null) {
            return;
        }

        request.setName(commonValidationHelper.normalizeRequiredText(request.getName()));
        request.setChaplain(commonValidationHelper.normalizeOptionalText(request.getChaplain()));
        request.setPhoneNumber(commonValidationHelper.normalizeOptionalText(request.getPhoneNumber()));
        request.setEmail(commonValidationHelper.normalizeOptionalText(request.getEmail()));
    }

    public void normalizeUpdateInput(OrganizationUnitUpdateRequest request) {
        if (request == null) {
            return;
        }

        request.setName(commonValidationHelper.normalizeOptionalText(request.getName()));
        request.setChaplain(commonValidationHelper.normalizeOptionalText(request.getChaplain()));
        request.setPhoneNumber(commonValidationHelper.normalizeOptionalText(request.getPhoneNumber()));
        request.setEmail(commonValidationHelper.normalizeOptionalText(request.getEmail()));
    }
}
