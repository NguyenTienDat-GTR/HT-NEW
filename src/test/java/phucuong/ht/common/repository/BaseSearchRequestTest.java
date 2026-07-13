package phucuong.ht.common.repository;

import org.junit.jupiter.api.Test;
import phucuong.ht.modules.auth.exception.ApiException;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaseSearchRequestTest {

    @Test
    void shouldRejectOutOfRangePagingValues() {
        BaseSearchRequest request = new BaseSearchRequest();

        assertThrows(ApiException.class, () -> request.setPage(-1));
        assertThrows(ApiException.class, () -> request.setPage(BaseSearchRequest.MAX_PAGE_NUMBER + 1));
        assertThrows(ApiException.class, () -> request.setSize(0));
        assertThrows(ApiException.class, () -> request.setSize(BaseSearchRequest.MAX_PAGE_SIZE + 1));
        assertDoesNotThrow(() -> request.setSize(BaseSearchRequest.MAX_PAGE_SIZE));
    }

    @Test
    void shouldLimitSearchAndFilterFanOut() {
        BaseSearchRequest request = new BaseSearchRequest();
        List<String> excessiveFields = java.util.stream.IntStream.range(0, BaseSearchRequest.MAX_SEARCH_FIELDS + 1)
                .mapToObj(index -> "field" + index)
                .toList();
        LinkedHashMap<String, Object> excessiveFilters = new LinkedHashMap<>();
        for (int index = 0; index <= BaseSearchRequest.MAX_FILTERS; index++) {
            excessiveFilters.put("field" + index, index);
        }

        assertThrows(ApiException.class, () -> request.setSearchFields(excessiveFields));
        assertThrows(ApiException.class, () -> request.setFilters(excessiveFilters));
    }
}
