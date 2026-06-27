package phucuong.ht.common.utils;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.auth.exception.ApiException;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

@Component
public class OrganizationUnitCodeGenerator {

    private static final int MAX_CODE_LENGTH = 50;

    public String generateDioceseCode(String unionName, Predicate<String> codeExists) {
        return generateCode(null, unionName, codeExists);
    }

    public String generateDeaneryCode(String dioceseCode, String associationName, Predicate<String> codeExists) {
        return generateCode(dioceseCode, associationName, codeExists);
    }

    public String generateParishCode(String deaneryCode, String chapterName, Predicate<String> codeExists) {
        return generateCode(deaneryCode, chapterName, codeExists);
    }

    public String generateCode(String namespace, String sourceName, Predicate<String> codeExists) {
        if (!StringUtils.hasText(sourceName)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Source name is required to generate code");
        }

        String slug = toSlug(sourceName);
        if (!StringUtils.hasText(slug)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Source name is invalid for code generation");
        }

        String namespaceSlug = StringUtils.hasText(namespace) ? toSlug(namespace) : "";
        String stem = namespaceSlug.isBlank() ? slug : namespaceSlug + "-" + slug;

        String candidate = trimToLength(stem, MAX_CODE_LENGTH);
        if (!codeExists.test(candidate)) {
            return candidate;
        }

        for (int index = 2; index < 10_000; index++) {
            String suffix = "-" + index;
            String uniqueCandidate = trimToLength(stem, MAX_CODE_LENGTH - suffix.length()) + suffix;
            if (!codeExists.test(uniqueCandidate)) {
                return uniqueCandidate;
            }
        }

        throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, errorCode.CONFLICT, "Unable to generate a unique code");
    }

    public String toSlug(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "")
                .replaceAll("-{2,}", "-");
    }

    private String trimToLength(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }

        String trimmed = value.substring(0, maxLength).replaceAll("-+$", "");
        return trimmed.isBlank() ? value.substring(0, maxLength) : trimmed;
    }
}
