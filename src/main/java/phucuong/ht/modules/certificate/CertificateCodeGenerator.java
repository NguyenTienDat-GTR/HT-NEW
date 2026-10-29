package phucuong.ht.modules.certificate;

import org.springframework.stereotype.Component;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class CertificateCodeGenerator {

    private final CommonValidationHelper commonValidationHelper;

    public CertificateCodeGenerator(CommonValidationHelper commonValidationHelper) {
        this.commonValidationHelper = commonValidationHelper;
    }

    // unitPrefix: "GP" cho khóa giáo phận, "GH" cho khóa giáo hạt.
    // Ví dụ: giáo phận Phú Cường + loại HTI + 2026 + seq 1 -> "GPPC-HTI-2026-1".
    public String buildCode(String unitPrefix, String unitName, TrainingCourseType courseType, Integer hostYear, long sequence) {
        if (hostYear == null) {
            throw commonValidationHelper.validationError("Năm tổ chức là bắt buộc để tạo mã chứng chỉ");
        }
        return unitAbbreviation(unitPrefix, unitName) + "-" + courseShortCode(courseType) + "-" + hostYear + "-" + sequence;
    }

    public String buildPrefix(String unitPrefix, String unitName, TrainingCourseType courseType, Integer hostYear) {
        if (hostYear == null) {
            throw commonValidationHelper.validationError("Năm tổ chức là bắt buộc để tạo mã chứng chỉ");
        }
        return unitAbbreviation(unitPrefix, unitName) + "-" + courseShortCode(courseType) + "-" + hostYear + "-";
    }

    // Ghép tiền tố cấp quản lý (GP/GH) với viết tắt tên đơn vị.
    public String unitAbbreviation(String unitPrefix, String unitName) {
        String prefix = unitPrefix == null ? "" : unitPrefix.trim().toUpperCase(Locale.ROOT);
        return prefix + abbreviation(unitName);
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

    // Ký hiệu trong mã chứng nhận của khóa quá khứ (khóa không có loại khóa).
    public static final String PAST_COURSE_SHORT_CODE = "QK";

    public String buildCodeWithShortCode(String unitPrefix, String unitName, String shortCode, Integer hostYear, long sequence) {
        if (hostYear == null) {
            throw commonValidationHelper.validationError("Năm tổ chức là bắt buộc để tạo mã chứng chỉ");
        }
        return unitAbbreviation(unitPrefix, unitName) + "-" + shortCode + "-" + hostYear + "-" + sequence;
    }

    public String buildPrefixWithShortCode(String unitPrefix, String unitName, String shortCode, Integer hostYear) {
        if (hostYear == null) {
            throw commonValidationHelper.validationError("Năm tổ chức là bắt buộc để tạo mã chứng chỉ");
        }
        return unitAbbreviation(unitPrefix, unitName) + "-" + shortCode + "-" + hostYear + "-";
    }

    // Mã viết tắt lấy từ cấu hình loại khóa; fallback về mã loại khi chưa khai báo.
    public String courseShortCode(TrainingCourseType courseType) {
        if (courseType == null) {
            throw commonValidationHelper.validationError("Loại khóa huấn luyện là bắt buộc để tạo mã chứng chỉ");
        }
        String shortCode = courseType.getCertificateShortCode();
        if (shortCode == null || shortCode.isBlank()) {
            shortCode = courseType.getTypeCode();
        }
        if (shortCode == null || shortCode.isBlank()) {
            throw commonValidationHelper.validationError("Loại khóa huấn luyện thiếu mã viết tắt để tạo mã chứng chỉ");
        }
        return normalizeAscii(shortCode).replaceAll("\\s+", "");
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
