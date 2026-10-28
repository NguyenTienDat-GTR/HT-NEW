package phucuong.ht.modules.search.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.search.model.QuickSearchGroup;
import phucuong.ht.modules.search.service.SearchService;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {
    private final SearchService searchService;

    @GetMapping("/quick")
    @PreAuthorize("isAuthenticated()")
    public apiResponse<List<QuickSearchGroup>> quick(
            @RequestParam("q") String q,
            @RequestParam(value = "limit", defaultValue = "5") int limit,
            Authentication authentication) {
        return apiResponse.success("Tìm kiếm nhanh thành công", searchService.quick(q, limit, authentication));
    }
}
