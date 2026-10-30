package phucuong.ht.common.repository;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Tiện ích dùng chung cho {@link BaseSearchRequest}: sao chép trước khi chèn thêm bộ lọc theo phạm vi
 * để không làm thay đổi request gốc của controller.
 */
public final class SearchRequests {

    private SearchRequests() {
    }

    public static BaseSearchRequest copy(BaseSearchRequest request) {
        BaseSearchRequest source = request == null ? new BaseSearchRequest() : request;
        BaseSearchRequest copy = new BaseSearchRequest();
        copy.setPage(source.getPage());
        copy.setSize(source.getSize());
        copy.setSearch(source.getSearch());
        copy.setSearchFields(source.getSearchFields() == null ? List.of() : List.copyOf(source.getSearchFields()));
        copy.setFilters(source.getFilters() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getFilters()));
        copy.setStatus(source.getStatus());
        copy.setSortBy(source.getSortBy());
        copy.setSortDirection(source.getSortDirection());
        return copy;
    }
}
