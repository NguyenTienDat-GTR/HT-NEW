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

import java.util.UUID;

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

    @GetMapping("/{id}")
    @PreAuthorize("@dioceseAccessEvaluator.canRead(authentication, #id)")
    public apiResponse<Diocese> findById(@PathVariable UUID id) {
        return apiResponse.success("Lấy thông tin giáo phận thành công", dioceseService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@dioceseAccessEvaluator.canUpdate(authentication, #id)")
    public apiResponse<Diocese> update(
            @PathVariable UUID id,
            @Valid @RequestBody DioceseUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật giáo phận thành công", dioceseService.update(id, request, authentication));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@dioceseAccessEvaluator.canDelete(authentication)")
    public apiResponse<Void> delete(@PathVariable UUID id) {
        dioceseService.delete(id);
        return apiResponse.success("Xóa giáo phận thành công", null);
    }
}
