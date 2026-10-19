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

    public String buildCode(String unitName, TrainingCourseType courseType, Integer hostYear, long sequence) {
        if (hostYear == null) {
            throw commonValidationHelper.validationError("Năm tổ chức là bắt buộc để tạo mã chứng chỉ");
        }
        return abbreviation(unitName) + "-" + courseShortCode(courseType) + "-" + hostYear + "-" + sequence;
    }

    public String buildPrefix(String unitName, TrainingCourseType courseType, Integer hostYear) {
        if (hostYear == null) {
            throw commonValidationHelper.validationError("Năm tổ chức là bắt buộc để tạo mã chứng chỉ");
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

    // Mã viết tắt lấy từ cấu hình loại khóa; fallback về mã loại khi chưa khai báo.
    public String courseShortCode(TrainingCourseType courseType) {
        if (courseType == null) {
            throw commonValidationHelper.validationError("Loại khóa học là bắt buộc để tạo mã chứng chỉ");
        }
        String shortCode = courseType.getCertificateShortCode();
        if (shortCode == null || shortCode.isBlank()) {
            shortCode = courseType.getTypeCode();
        }
        if (shortCode == null || shortCode.isBlank()) {
            throw commonValidationHelper.validationError("Loại khóa học thiếu mã viết tắt để tạo mã chứng chỉ");
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
