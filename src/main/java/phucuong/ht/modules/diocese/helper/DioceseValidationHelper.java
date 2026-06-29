package phucuong.ht.modules.diocese.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.common.validation.OrganizationUnitValidationHelper;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.model.request.DioceseCreateRequest;
import phucuong.ht.modules.diocese.model.request.DioceseUpdateRequest;
import phucuong.ht.modules.diocese.repository.DioceseRepository;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DioceseValidationHelper {

    private final DioceseRepository dioceseRepository;
    private final CommonValidationHelper commonValidationHelper;
    private final OrganizationUnitValidationHelper organizationUnitValidationHelper;

    public void validateDioceseId(UUID id) {
        commonValidationHelper.requireId(id, "ID giáo phận là bắt buộc");
    }

    public Diocese requireExistingDiocese(UUID id) {
        validateDioceseId(id);
        return dioceseRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy giáo phận: " + id));
    }

    public void validateCreateInput(DioceseCreateRequest request) {
        organizationUnitValidationHelper.normalizeCreateInput(request);
        if (request != null) {
            request.setUnionName(commonValidationHelper.normalizeRequiredText(request.getUnionName()));
        }
    }

    public void validateUpdateInput(DioceseUpdateRequest request) {
        organizationUnitValidationHelper.normalizeUpdateInput(request);
        if (request != null) {
            request.setUnionName(commonValidationHelper.normalizeOptionalText(request.getUnionName()));
        }
    }

    public void validateCreateNameUniqueness(String name) {
        if (dioceseRepository.existsByName(name)) {
            throw commonValidationHelper.conflict("Tên giáo phận đã tồn tại");
        }
    }

    public void validateUpdateNameUniqueness(UUID id, String requestedName, String currentName) {
        if (!commonValidationHelper.isChanged(requestedName, currentName)) {
            return;
        }

        if (dioceseRepository.existsByNameAndIdNot(requestedName, id)) {
            throw commonValidationHelper.conflict("Tên giáo phận đã tồn tại");
        }
    }

    public String getRequiredAuditActor(Authentication authentication) {
        return commonValidationHelper.requireAuditActor(
                authentication,
                "Tài khoản hiện tại không có thông tin người thực hiện để ghi nhận lịch sử"
        );
    }
}
