package phucuong.ht.modules.diocese.helper;

import org.springframework.http.HttpStatus;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.auth.service.SiteHostPolicy;

import java.util.Locale;
import java.util.regex.Pattern;

/** Quy tắc đặt subdomain cho giáo phận: nhãn DNS chữ thường, 3-63 ký tự, không dùng nhãn dành riêng. */
public final class SiteSubdomainRules {

    private static final Pattern LABEL = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,61})[a-z0-9]$");

    private SiteSubdomainRules() {
    }

    /** Chuẩn hóa về chữ thường và kiểm tra; ném 400 nếu không hợp lệ. */
    public static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!LABEL.matcher(value).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                    "Subdomain chỉ gồm chữ thường không dấu, số và dấu gạch ngang (3-63 ký tự), không bắt đầu hoặc kết thúc bằng dấu gạch ngang");
        }
        if (SiteHostPolicy.RESERVED_LABELS.contains(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                    "Subdomain \"" + value + "\" được dành riêng cho hệ thống, vui lòng chọn tên khác");
        }
        return value;
    }
}
