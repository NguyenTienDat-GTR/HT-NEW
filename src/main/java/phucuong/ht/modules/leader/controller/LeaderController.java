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
import phucuong.ht.modules.rankHistory.model.response.RankHistoryResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leaders")
@RequiredArgsConstructor
public class LeaderController {

    private final LeaderService leaderService;

    @PostMapping
    @PreAuthorize("@leaderAccessEvaluator.canCreate(authentication)")
    public apiResponse<LeaderResponse> create(@Valid @RequestBody LeaderCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tạo Huynh Trưởng thành công", leaderService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@leaderAccessEvaluator.canList(authentication)")
    public apiResponse<Page<LeaderResponse>> findAll(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách Huynh Trưởng thành công", leaderService.findAll(request, authentication));
    }

    @GetMapping("/account-candidates")
    @PreAuthorize("@leaderAccessEvaluator.canList(authentication)")
    public apiResponse<List<LeaderResponse>> accountCandidates(BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách Huynh Trưởng chưa có tài khoản thành công", leaderService.findAccountCandidates(request, authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@leaderAccessEvaluator.canRead(authentication, #id)")
    public apiResponse<LeaderResponse> findById(@PathVariable UUID id, Authentication authentication) {
        return apiResponse.success("Lấy thông tin Huynh Trưởng thành công", leaderService.findById(id, authentication));
    }

    @GetMapping("/{id}/rank-history")
    @PreAuthorize("@leaderAccessEvaluator.canRead(authentication, #id)")
    public apiResponse<List<RankHistoryResponse>> rankHistory(@PathVariable UUID id) {
        return apiResponse.success("Lấy lịch sử cấp bậc Huynh Trưởng thành công", leaderService.findRankHistory(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@leaderAccessEvaluator.canUpdate(authentication, #id)")
    public apiResponse<LeaderResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody LeaderUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật Huynh Trưởng thành công", leaderService.update(id, request, authentication));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("@leaderAccessEvaluator.canToggle(authentication, #id)")
    public apiResponse<LeaderResponse> toggleStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusToggleRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Đổi trạng thái Huynh Trưởng thành công", leaderService.toggleStatus(id, request, authentication));
    }
}
