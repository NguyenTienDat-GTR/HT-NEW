package phucuong.ht.common.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import phucuong.ht.common.utils.InMemorySortSupport;

import java.util.List;
import java.util.function.Function;

/**
 * Phân trang trên danh sách đã nạp sẵn trong bộ nhớ (dùng cho các màn quản trị phải lọc theo quyền trước khi cắt trang).
 */
public final class InMemoryPager {

    private InMemoryPager() {
    }

    /** Cắt trang rồi ánh xạ từng phần tử sang response. */
    public static <T, R> Page<R> page(BaseSearchRequest request, List<T> items, Function<T, R> mapper) {
        int page = request == null ? 0 : request.getPage();
        int size = request == null ? 10 : request.getSize();
        int start = Math.min(page * size, items.size());
        int end = Math.min(start + size, items.size());
        return new PageImpl<>(items.subList(start, end).stream().map(mapper).toList(), PageRequest.of(page, size), items.size());
    }

    /** Sắp xếp theo sortBy/sortDirection của request (nếu có) rồi cắt trang; phần tử đã là response. */
    public static <T> Page<T> sortedPage(BaseSearchRequest request, List<T> items) {
        int page = request == null ? 0 : request.getPage();
        int size = request == null ? 10 : request.getSize();
        List<T> ordered = request == null ? items : InMemorySortSupport.sort(items, request.getSortBy(), request.getSortDirection());
        int start = Math.min(page * size, ordered.size());
        int end = Math.min(start + size, ordered.size());
        return new PageImpl<>(ordered.subList(start, end), PageRequest.of(page, size), ordered.size());
    }
}
