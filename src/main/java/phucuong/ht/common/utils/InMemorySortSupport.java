package phucuong.ht.common.utils;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sắp xếp danh sách response đã nạp sẵn trong bộ nhớ theo tên thuộc tính (cột hiển thị), dùng cho các màn không truy vấn qua BaseRepo.
 * sortBy có thể ghép nhiều thuộc tính bằng dấu +; thuộc tính không tồn tại hoặc không so sánh được thì bỏ qua.
 */
public final class InMemorySortSupport {

    private InMemorySortSupport() {
    }

    public static <T> List<T> sort(List<T> items, String sortBy, String sortDirection) {
        if (items == null || items.size() < 2) {
            return items;
        }
        if (!StringUtils.hasText(sortBy)) {
            // Mặc định: bản ghi tạo gần nhất đứng trước (nếu phần tử có createdAt).
            return isReadable(items.get(0), "createdAt") ? sort(items, "createdAt", "DESC") : items;
        }
        Comparator<T> comparator = null;
        for (String property : sortBy.split("\\+")) {
            String name = property.trim();
            if (name.isEmpty() || !isReadable(items.get(0), name)) {
                continue;
            }
            Comparator<T> next = Comparator.comparing(item -> valueOf(item, name), Comparator.nullsLast(InMemorySortSupport::compareValues));
            comparator = comparator == null ? next : comparator.thenComparing(next);
        }
        if (comparator == null) {
            return items;
        }
        if ("DESC".equalsIgnoreCase(sortDirection)) {
            comparator = comparator.reversed();
        }
        List<T> sorted = new ArrayList<>(items);
        sorted.sort(comparator);
        return sorted;
    }

    private static boolean isReadable(Object item, String property) {
        return new BeanWrapperImpl(item).isReadableProperty(property);
    }

    private static Object valueOf(Object item, String property) {
        BeanWrapper wrapper = new BeanWrapperImpl(item);
        return wrapper.getPropertyValue(property);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareValues(Object left, Object right) {
        if (left instanceof String a && right instanceof String b) {
            return a.compareToIgnoreCase(b);
        }
        if (left instanceof Comparable comparable && left.getClass().equals(right.getClass())) {
            return comparable.compareTo(right);
        }
        return String.valueOf(left).compareToIgnoreCase(String.valueOf(right));
    }
}
