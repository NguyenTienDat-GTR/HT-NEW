package phucuong.ht.modules.system.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.system.model.response.SystemMonitoringResponse;
import phucuong.ht.modules.system.service.SystemMonitoringService;

@RestController
@RequestMapping("/api/system/monitoring")
@RequiredArgsConstructor
public class SystemMonitoringController {

    private final SystemMonitoringService systemMonitoringService;

    @GetMapping("/overview")
    @PreAuthorize("@systemLogAccessEvaluator.canReadAll(authentication)")
    public apiResponse<SystemMonitoringResponse> overview(@RequestParam(defaultValue = "24") int hours) {
        return apiResponse.success("Lấy số liệu giám sát hệ thống thành công", systemMonitoringService.overview(hours));
    }
}
