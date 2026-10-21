package phucuong.ht.common.repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.http.HttpStatus;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.auth.exception.ApiException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Bộ lọc theo năm của một trường ngày: khóa bộ lọc `issueDate:year` với một năm hoặc danh sách năm (khớp bất kỳ năm nào).
 * Hỗ trợ cột LocalDate và OffsetDateTime (múi giờ Việt Nam, UTC+7); được chuyển thành khoảng 01/01 - 31/12 để dùng được chỉ mục.
 */
final class YearFilterPredicate {

    static final String SUFFIX = ":year";
    private static final ZoneOffset VIETNAM = ZoneOffset.ofHours(7);

    private YearFilterPredicate() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Predicate build(CriteriaBuilder cb, Path<?> path, Object filterValue) {
        Collection<?> values = filterValue instanceof Collection<?> collection ? collection : List.of(filterValue);
        if (values.isEmpty()) {
            throw invalid("Danh sách năm lọc đang trống");
        }
        Class<?> type = path.getJavaType();
        List<Predicate> ranges = new ArrayList<>();
        for (Object value : values) {
            int year = parseYear(value);
            if (LocalDate.class.equals(type)) {
                ranges.add(cb.between((Expression) path, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)));
            } else if (OffsetDateTime.class.equals(type)) {
                ranges.add(cb.and(
                        cb.greaterThanOrEqualTo((Expression) path, OffsetDateTime.of(year, 1, 1, 0, 0, 0, 0, VIETNAM)),
                        cb.lessThan((Expression) path, OffsetDateTime.of(year + 1, 1, 1, 0, 0, 0, 0, VIETNAM))));
            } else {
                throw invalid("Không lọc theo năm được trên trường kiểu " + type.getSimpleName());
            }
        }
        return ranges.size() == 1 ? ranges.get(0) : cb.or(ranges.toArray(Predicate[]::new));
    }

    static int parseYear(Object value) {
        try {
            int year = Integer.parseInt(String.valueOf(value).trim());
            if (year >= 1 && year <= 9998) {
                return year;
            }
        } catch (NumberFormatException ignored) {
            // rơi xuống báo lỗi bên dưới
        }
        throw invalid("Năm lọc không hợp lệ: " + value);
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, message);
    }
}
