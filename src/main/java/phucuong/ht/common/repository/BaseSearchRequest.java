package phucuong.ht.common.repository;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.auth.exception.ApiException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
public class BaseSearchRequest {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_PAGE_NUMBER = 1_000_000;
    public static final int MAX_SEARCH_FIELDS = 10;
    public static final int MAX_FILTERS = 20;

    private int page = 0;

    private int size = 10;

    private String search;

    private List<String> searchFields = List.of();

    private Map<String, Object> filters = new LinkedHashMap<>();

    private Boolean status;

    private String sortBy;

    private String sortDirection = "ASC";

    public void setPage(int page) {
        if (page > MAX_PAGE_NUMBER) {
            throw invalid("page không được vượt quá " + MAX_PAGE_NUMBER);
        }
        if (page < 0) {
            throw invalid("page phải lớn hơn hoặc bằng 0");
        }
        this.page = page;
    }

    public void setSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw invalid("size phải từ 1 đến " + MAX_PAGE_SIZE);
        }
        this.size = size;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public void setSearchFields(List<String> searchFields) {
        List<String> normalized = searchFields == null ? List.of() : List.copyOf(searchFields);
        if (normalized.size() > MAX_SEARCH_FIELDS) {
            throw invalid("searchFields không được vượt quá " + MAX_SEARCH_FIELDS + " trường");
        }
        this.searchFields = normalized;
    }

    public void setFilters(Map<String, Object> filters) {
        Map<String, Object> normalized = filters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(filters);
        if (normalized.size() > MAX_FILTERS) {
            throw invalid("filters không được vượt quá " + MAX_FILTERS + " trường");
        }
        this.filters = normalized;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, message);
    }
}
