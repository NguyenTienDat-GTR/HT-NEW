package phucuong.ht.modules.diocese.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.OrganizationUnitCodeGenerator;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.model.request.DioceseCreateRequest;
import phucuong.ht.modules.diocese.model.request.DioceseUpdateRequest;
import phucuong.ht.modules.diocese.repository.DioceseRepository;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class DioceseService {

    private final DioceseRepository dioceseRepository;
    private final OrganizationUnitCodeGenerator organizationUnitCodeGenerator;
    private final securityUtils securityUtils;

    public Diocese create(DioceseCreateRequest request, Authentication authentication) {
        String auditActor = getRequiredAuditActor(authentication);
        try {
            Diocese diocese = new Diocese();
            diocese.setDioceseCode(organizationUnitCodeGenerator.generateDioceseCode(
                    request.getUnionName(),
                    dioceseRepository::existsByDioceseCode
            ));
            diocese.setName(request.getName());
            diocese.setPatronSaintDay(request.getPatronSaintDay());
            diocese.setEstablishmentDate(request.getEstablishmentDate());
            diocese.setChaplain(request.getChaplain());
            diocese.setPhoneNumber(request.getPhoneNumber());
            diocese.setEmail(request.getEmail());
            diocese.setUnionName(request.getUnionName());
            diocese.setStatus(Boolean.TRUE);
            diocese.setCreatedAt(OffsetDateTime.now());
            diocese.setUpdatedAt(diocese.getCreatedAt());
            diocese.setCreatedBy(auditActor);
            diocese.setUpdatedBy(auditActor);
            return dioceseRepository.save(diocese);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu giáo phận bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    public Page<Diocese> findAll(BaseSearchRequest request) {
        return dioceseRepository.findAll(request == null ? new BaseSearchRequest() : request);
    }

    public Diocese findById(String dioceseCode) {
        return getExistingDiocese(dioceseCode);
    }

    public Diocese update(String dioceseCode, DioceseUpdateRequest request, Authentication authentication) {
        validateDioceseCode(dioceseCode);
        getExistingDiocese(dioceseCode);
        String auditActor = getRequiredAuditActor(authentication);
        try {
            return dioceseRepository.update(dioceseCode, request, auditActor);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Dữ liệu giáo phận bị trùng hoặc xung đột với dữ liệu hiện có");
        }
    }

    public void delete(String dioceseCode) {
        validateDioceseCode(dioceseCode);
        getExistingDiocese(dioceseCode);
        try {
            dioceseRepository.delete(dioceseCode);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, "Không thể xóa giáo phận vì đang được dữ liệu khác tham chiếu");
        }
    }

    private Diocese getExistingDiocese(String dioceseCode) {
        validateDioceseCode(dioceseCode);
        return dioceseRepository.findById(dioceseCode)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        errorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy giáo phận: " + dioceseCode
                ));
    }

    private void validateDioceseCode(String dioceseCode) {
        if (!StringUtils.hasText(dioceseCode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Mã giáo phận là bắt buộc");
        }
    }

    private String getRequiredAuditActor(Authentication authentication) {
        String auditActor = securityUtils.currentAuditActor(authentication);
        if (!StringUtils.hasText(auditActor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, "Tài khoản hiện tại không có thông tin người thực hiện để ghi nhận lịch sử");
        }
        return auditActor;
    }
}
