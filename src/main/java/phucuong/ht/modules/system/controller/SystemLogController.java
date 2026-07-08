package phucuong.ht.modules.system.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.system.model.response.SystemLogResponse;
import phucuong.ht.modules.system.service.SystemLogService;

@RestController
@RequestMapping("/api/system/audit-logs")
@RequiredArgsConstructor
public class SystemLogController {

    private final SystemLogService systemLogService;

    @GetMapping
    @PreAuthorize("@systemLogAccessEvaluator.canList(authentication)")
    public apiResponse<Page<SystemLogResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách audit log thành công", systemLogService.findAll(request, authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@systemLogAccessEvaluator.canRead(authentication, #id)")
    public apiResponse<SystemLogResponse> findById(@PathVariable String id) {
        return apiResponse.success("Lấy audit log thành công", systemLogService.findById(id));
    }
}
