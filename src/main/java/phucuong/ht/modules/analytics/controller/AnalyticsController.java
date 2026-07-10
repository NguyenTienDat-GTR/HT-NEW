package phucuong.ht.modules.analytics.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.analytics.model.request.AnalyticsFilterRequest;
import phucuong.ht.modules.analytics.model.response.AnalyticsResponse;
import phucuong.ht.modules.analytics.service.AnalyticsService;

@RestController @RequestMapping("/api/analytics") @RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService analyticsService;
    @GetMapping("/overview") @PreAuthorize("@analyticsAccessEvaluator.canReadOverview(authentication)")
    public apiResponse<AnalyticsResponse> overview(@ModelAttribute AnalyticsFilterRequest request, Authentication authentication) { return apiResponse.success(null, analyticsService.overview(request, authentication)); }
    @GetMapping("/organization") @PreAuthorize("@analyticsAccessEvaluator.canReadDetailed(authentication)")
    public apiResponse<AnalyticsResponse> organization(@ModelAttribute AnalyticsFilterRequest request, Authentication authentication) { return apiResponse.success(null, analyticsService.organization(request, authentication)); }
    @GetMapping("/leaders") @PreAuthorize("@analyticsAccessEvaluator.canReadDetailed(authentication)")
    public apiResponse<AnalyticsResponse> leaders(@ModelAttribute AnalyticsFilterRequest request, Authentication authentication) { return apiResponse.success(null, analyticsService.leaders(request, authentication)); }
    @GetMapping("/courses") @PreAuthorize("@analyticsAccessEvaluator.canReadDetailed(authentication)")
    public apiResponse<AnalyticsResponse> courses(@ModelAttribute AnalyticsFilterRequest request, Authentication authentication) { return apiResponse.success(null, analyticsService.courses(request, authentication)); }
    @GetMapping("/certificates") @PreAuthorize("@analyticsAccessEvaluator.canReadDetailed(authentication)")
    public apiResponse<AnalyticsResponse> certificates(@ModelAttribute AnalyticsFilterRequest request, Authentication authentication) { return apiResponse.success(null, analyticsService.certificates(request, authentication)); }
}
