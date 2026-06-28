package phucuong.ht.modules.deanery.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.deanery.model.request.DeaneryCreateRequest;
import phucuong.ht.modules.deanery.model.request.DeaneryUpdateRequest;
import phucuong.ht.modules.deanery.model.response.DeaneryResponse;
import phucuong.ht.modules.deanery.service.DeaneryService;

@RestController
@RequestMapping("/api/deaneries")
@RequiredArgsConstructor
public class DeaneryController {

    private final DeaneryService deaneryService;

    @PostMapping
    @PreAuthorize("@deaneryAccessEvaluator.canCreate(authentication)")
    public apiResponse<DeaneryResponse> create(@Valid @RequestBody DeaneryCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tạo giáo hạt thành công", deaneryService.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@deaneryAccessEvaluator.canList(authentication)")
    public apiResponse<Page<DeaneryResponse>> findAll(@ModelAttribute BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách giáo hạt thành công", deaneryService.findAll(request, authentication));
    }

    @GetMapping("/{deaneryCode}")
    @PreAuthorize("@deaneryAccessEvaluator.canRead(authentication, #deaneryCode)")
    public apiResponse<DeaneryResponse> findById(@PathVariable String deaneryCode) {
        return apiResponse.success("Lấy thông tin giáo hạt thành công", deaneryService.findById(deaneryCode));
    }

    @PutMapping("/{deaneryCode}")
    @PreAuthorize("@deaneryAccessEvaluator.canUpdate(authentication, #deaneryCode)")
    public apiResponse<DeaneryResponse> update(
            @PathVariable String deaneryCode,
            @Valid @RequestBody DeaneryUpdateRequest request,
            Authentication authentication
    ) {
        return apiResponse.success("Cập nhật giáo hạt thành công", deaneryService.update(deaneryCode, request, authentication));
    }

    @DeleteMapping("/{deaneryCode}")
    @PreAuthorize("@deaneryAccessEvaluator.canDelete(authentication)")
    public apiResponse<Void> delete(@PathVariable String deaneryCode, Authentication authentication) {
        deaneryService.delete(deaneryCode, authentication);
        return apiResponse.success("Xóa giáo hạt thành công", null);
    }
}
