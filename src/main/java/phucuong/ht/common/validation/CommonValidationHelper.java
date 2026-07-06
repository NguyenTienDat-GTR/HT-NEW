package phucuong.ht.common.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.exception.ApiException;

import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CommonValidationHelper {

    private final securityUtils securityUtils;

    public void requireId(UUID id, String message) {
        if (id == null) {
            throw validationError(message);
        }
    }

    public boolean isChanged(String requestedValue, String currentValue) {
        return StringUtils.hasText(requestedValue) && !Objects.equals(requestedValue, currentValue);
    }

    public String requireAuditActor(Authentication authentication, String message) {
        String auditActor = securityUtils.currentAuditActor(authentication);
        if (!StringUtils.hasText(auditActor)) {
            throw forbidden(message);
        }
        return auditActor;
    }

    public String normalizeRequiredText(String value) {
        return value == null ? null : value.trim();
    }

    public String normalizeOptionalText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public void requireStatusToggleConfirmation(String confirmationText) {
        if (!"Xác nhận".equals(normalizeRequiredText(confirmationText))) {
            throw validationError("Vui lòng nhập chính xác \"Xác nhận\" để đổi trạng thái");
        }
    }

    public ApiException validationError(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, message);
    }

    public ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, errorCode.ACCESS_DENIED, message);
    }

    public ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, errorCode.CONFLICT, message);
    }

    public ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, errorCode.RESOURCE_NOT_FOUND, message);
    }
}
