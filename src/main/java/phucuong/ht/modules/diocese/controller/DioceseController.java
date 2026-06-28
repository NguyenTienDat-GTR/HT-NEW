package phucuong.ht.modules.diocese.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.model.request.DioceseCreateRequest;
import phucuong.ht.modules.diocese.model.request.DioceseUpdateRequest;
import phucuong.ht.modules.diocese.service.DioceseService;

@RestController
@RequestMapping("/api/dioceses")
@RequiredArgsConstructor
public class DioceseController {

    private final DioceseService dioceseService;

    @PostMapping
    @PreAuthorize("@dioceseAccessEvaluator.canCreate(authentication)")
    public apiResponse<Diocese> create(@Valid @RequestBody DioceseCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tạo giáo phận thành công", dioceseService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@dioceseAccessEvaluator.canList(authentication)")
    public apiResponse<Page<Diocese>> findAll(@ModelAttribute BaseSearchRequest request) {
        return apiResponse.success("Lấy danh sách giáo phận thành công", dioceseService.findAll(request));
    }

    @GetMapping("/{dioceseCode}")
    @PreAuthorize("@dioceseAccessEvaluator.canRead(authentication, #dioceseCode)")
    public apiResponse<Diocese> findById(@PathVariable String dioceseCode) {
        return apiResponse.success("Lấy thông tin giáo phận thành công", dioceseService.findById(dioceseCode));
    }

    @PutMapping("/{dioceseCode}")
    @PreAuthorize("@dioceseAccessEvaluator.canUpdate(authentication, #dioceseCode)")
    public apiResponse<Diocese> update(
            @PathVariable String dioceseCode,
            @Valid @RequestBody DioceseUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật giáo phận thành công", dioceseService.update(dioceseCode, request, authentication));
    }

    @DeleteMapping("/{dioceseCode}")
    @PreAuthorize("@dioceseAccessEvaluator.canDelete(authentication)")
    public apiResponse<Void> delete(@PathVariable String dioceseCode) {
        dioceseService.delete(dioceseCode);
        return apiResponse.success("Xóa giáo phận thành công", null);
    }
}
