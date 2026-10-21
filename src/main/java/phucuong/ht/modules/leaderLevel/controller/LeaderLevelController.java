package phucuong.ht.modules.leaderLevel.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.modules.leaderLevel.model.request.LeaderLevelReorderRequest;
import phucuong.ht.modules.leaderLevel.model.request.LeaderLevelRequest;
import phucuong.ht.modules.leaderLevel.model.response.LeaderLevelResponse;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;

import java.util.List;

// Danh mục cấp độ huynh trưởng riêng của từng giáo phận: admin giáo phận (quyền system.leader_level.*.diocese) quản lý danh mục
// của giáo phận mình và phân quyền tiếp cho vai trò thấp hơn qua cơ chế gán quyền theo phạm vi. /options cho mọi tài khoản đăng nhập để dựng ô chọn cấp độ.
@RestController
@RequestMapping("/api/leader-levels")
@RequiredArgsConstructor
public class LeaderLevelController {

    private static final String CAN_READ = "@securityUtils.hasAuthority(authentication, 'system.leader_level.read.diocese')";
    private static final String CAN_CREATE = "@securityUtils.hasAuthority(authentication, 'system.leader_level.create.diocese')";
    private static final String CAN_UPDATE = "@securityUtils.hasAuthority(authentication, 'system.leader_level.update.diocese')";
    private static final String CAN_DELETE = "@securityUtils.hasAuthority(authentication, 'system.leader_level.delete.diocese')";

    private final LeaderLevelService service;

    @GetMapping("/options")
    @PreAuthorize("isAuthenticated()")
    public apiResponse<List<LeaderLevelResponse>> options(Authentication authentication) {
        return apiResponse.success("Lấy danh sách cấp độ thành công", service.options(authentication));
    }

    @GetMapping
    @PreAuthorize(CAN_READ)
    public apiResponse<List<LeaderLevelResponse>> list(Authentication authentication) {
        return apiResponse.success("Lấy danh mục cấp độ thành công", service.list(authentication));
    }

    @PostMapping
    @PreAuthorize(CAN_CREATE)
    public apiResponse<LeaderLevelResponse> create(@RequestBody LeaderLevelRequest request, Authentication authentication) {
        return apiResponse.success("Tạo cấp độ thành công", service.create(request, authentication));
    }

    @PutMapping("/reorder")
    @PreAuthorize(CAN_UPDATE)
    public apiResponse<List<LeaderLevelResponse>> reorder(@Valid @RequestBody LeaderLevelReorderRequest request, Authentication authentication) {
        return apiResponse.success("Cập nhật thứ tự cấp độ thành công", service.reorder(request, authentication));
    }

    @PutMapping("/{levelCode}")
    @PreAuthorize(CAN_UPDATE)
    public apiResponse<LeaderLevelResponse> update(@PathVariable String levelCode, @RequestBody LeaderLevelRequest request, Authentication authentication) {
        return apiResponse.success("Cập nhật cấp độ thành công", service.update(levelCode, request, authentication));
    }

    @DeleteMapping("/{levelCode}")
    @PreAuthorize(CAN_DELETE)
    public apiResponse<Void> delete(@PathVariable String levelCode, Authentication authentication) {
        service.delete(levelCode, authentication);
        return apiResponse.<Void>success("Xóa cấp độ thành công", null);
    }
}
