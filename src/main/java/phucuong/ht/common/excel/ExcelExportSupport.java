package phucuong.ht.common.excel;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.auth.exception.ApiException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

/**
 * Gom toàn bộ kết quả của một danh sách theo đúng bộ lọc/tìm kiếm/sắp xếp đang dùng, bằng cách gọi lại chính
 * hàm tìm kiếm của danh sách (nên giữ nguyên phạm vi quyền) theo từng trang tối đa 100 dòng.
 */
public final class ExcelExportSupport {

    public static final int MAX_EXPORT_ROWS = 50_000;

    private ExcelExportSupport() {
    }

    public static <R> List<R> collectAll(BaseSearchRequest request, Function<BaseSearchRequest, Page<R>> fetch) {
        return collectAll(request, fetch, phucuong.ht.common.excel.job.JobProgress.NONE);
    }

    /** Như trên, báo tiến độ (đã lấy / tổng) sau mỗi trang; tác vụ nền có thể bị hủy ở các điểm này. */
    public static <R> List<R> collectAll(BaseSearchRequest request, Function<BaseSearchRequest, Page<R>> fetch,
                                         phucuong.ht.common.excel.job.JobProgress progress) {
        List<R> all = new ArrayList<>();
        int page = 0;
        while (true) {
            Page<R> result = fetch.apply(pageOf(request, page));
            if (result.getTotalElements() > MAX_EXPORT_ROWS) {
                throw new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                        "Có " + result.getTotalElements() + " dòng, vượt quá giới hạn xuất " + MAX_EXPORT_ROWS
                                + " dòng. Vui lòng lọc bớt dữ liệu rồi xuất lại.");
            }
            all.addAll(result.getContent());
            progress.report(all.size(), (int) result.getTotalElements());
            if (!result.hasNext() || result.getContent().isEmpty()) {
                return all;
            }
            page++;
        }
    }

    private static BaseSearchRequest pageOf(BaseSearchRequest source, int page) {
        BaseSearchRequest copy = new BaseSearchRequest();
        copy.setPage(page);
        copy.setSize(BaseSearchRequest.MAX_PAGE_SIZE);
        if (source != null) {
            copy.setSearch(source.getSearch());
            copy.setSearchFields(source.getSearchFields() == null ? List.of() : List.copyOf(source.getSearchFields()));
            copy.setFilters(source.getFilters() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getFilters()));
            copy.setStatus(source.getStatus());
            copy.setSortBy(source.getSortBy());
            copy.setSortDirection(source.getSortDirection());
        }
        return copy;
    }
}
