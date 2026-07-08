package phucuong.ht.modules.leader.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.leader.model.request.LeaderCreateRequest;
import phucuong.ht.modules.leader.model.request.LeaderUpdateRequest;
import phucuong.ht.modules.leader.model.response.LeaderResponse;
import phucuong.ht.modules.leader.service.LeaderService;

import java.util.UUID;

@RestController
@RequestMapping("/api/leaders")
@RequiredArgsConstructor
public class LeaderController {

    private final LeaderService leaderService;

    @PostMapping
    @PreAuthorize("@leaderAccessEvaluator.canCreate(authentication)")
    public apiResponse<LeaderResponse> create(@Valid @RequestBody LeaderCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tạo leader thành công", leaderService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@leaderAccessEvaluator.canList(authentication)")
    public apiResponse<Page<LeaderResponse>> findAll(@ModelAttribute BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách leader thành công", leaderService.findAll(request, authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@leaderAccessEvaluator.canRead(authentication, #id)")
    public apiResponse<LeaderResponse> findById(@PathVariable UUID id, Authentication authentication) {
        return apiResponse.success("Lấy thông tin leader thành công", leaderService.findById(id, authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@leaderAccessEvaluator.canUpdate(authentication, #id)")
    public apiResponse<LeaderResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody LeaderUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật leader thành công", leaderService.update(id, request, authentication));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("@leaderAccessEvaluator.canToggle(authentication, #id)")
    public apiResponse<LeaderResponse> toggleStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái leader thành công", leaderService.toggleStatus(id, request, authentication));
    }
}
