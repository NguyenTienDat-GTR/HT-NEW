package phucuong.ht.modules.certificate;

import org.springframework.stereotype.Component;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.training.enums.CourseType;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class CertificateCodeGenerator {

    private final CommonValidationHelper commonValidationHelper;

    public CertificateCodeGenerator(CommonValidationHelper commonValidationHelper) {
        this.commonValidationHelper = commonValidationHelper;
    }

    public String buildCode(String unitName, CourseType courseType, Integer hostYear, long sequence) {
        if (hostYear == null) {
            throw commonValidationHelper.validationError("hostYear là bắt buộc để tạo mã chứng chỉ");
        }
        return abbreviation(unitName) + "-" + courseShortCode(courseType) + "-" + hostYear + "-" + sequence;
    }

    public String buildPrefix(String unitName, CourseType courseType, Integer hostYear) {
        if (hostYear == null) {
            throw commonValidationHelper.validationError("hostYear là bắt buộc để tạo mã chứng chỉ");
        }
        return abbreviation(unitName) + "-" + courseShortCode(courseType) + "-" + hostYear + "-";
    }

    public String abbreviation(String unitName) {
        String normalized = normalizeAscii(unitName);
        if (normalized.isBlank()) {
            throw commonValidationHelper.validationError("Tên đơn vị là bắt buộc để tạo mã chứng chỉ");
        }
        StringBuilder builder = new StringBuilder();
        for (String token : normalized.split("\\s+")) {
            if (!token.isBlank()) {
                builder.append(Character.toUpperCase(token.charAt(0)));
            }
        }
        if (builder.isEmpty()) {
            throw commonValidationHelper.validationError("Tên đơn vị là bắt buộc để tạo mã chứng chỉ");
        }
        return builder.toString();
    }

    public String courseShortCode(CourseType courseType) {
        if (courseType == null) {
            throw commonValidationHelper.validationError("Loại khóa học là bắt buộc để tạo mã chứng chỉ");
        }
        return switch (courseType) {
            case SMTL_DT, SMKN_DT -> "DT";
            case SMTL_I, SMKN_I, TSM_II, SMTL_II, SMKN_II, TSM_III_1, TSM_III_2 -> courseType.name().replace("_", "");
            case SMTL_III, SMKN_III -> "III";
            case HLV_I -> "HLVI";
            case HLV_II -> "HLVII";
            case HLV_III -> "HLVIII";
        };
    }

    private String normalizeAscii(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('Đ', 'D')
                .replace('đ', 'd');
        return normalized.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9\\s]", " ").trim();
    }
}
