package phucuong.ht.common.repository;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class BaseSearchRequest {

    private int page = 0;

    private int size = 10;

    private String search;

    private List<String> searchFields = List.of();

    private Map<String, Object> filters = new LinkedHashMap<>();

    private Boolean status;

    private String sortBy;

    private String sortDirection = "ASC";
}
