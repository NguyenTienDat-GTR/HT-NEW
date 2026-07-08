package phucuong.ht.modules.training.scoreFormulaTemplate.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.training.scoreFormulaTemplate.model.request.ScoreFormulaTemplateCreateRequest;
import phucuong.ht.modules.training.scoreFormulaTemplate.model.request.ScoreFormulaTemplateUpdateRequest;
import phucuong.ht.modules.training.scoreFormulaTemplate.model.response.ScoreFormulaTemplateResponse;
import phucuong.ht.modules.training.scoreFormulaTemplate.service.ScoreFormulaTemplateService;

@RestController
@RequestMapping("/api/training/score-formulas")
@RequiredArgsConstructor
public class ScoreFormulaTemplateController {

    private final ScoreFormulaTemplateService service;

    @PostMapping
    @PreAuthorize("@scoreFormulaTemplateAccessEvaluator.canCreate(authentication, #request.unitType)")
    public apiResponse<ScoreFormulaTemplateResponse> create(@Valid @RequestBody ScoreFormulaTemplateCreateRequest request, Authentication authentication) {
        return apiResponse.success("Tạo công thức tính điểm thành công", service.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@scoreFormulaTemplateAccessEvaluator.canList(authentication)")
    public apiResponse<Page<ScoreFormulaTemplateResponse>> findAll(@ModelAttribute BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Lấy danh sách công thức tính điểm thành công", service.findAll(request, authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@scoreFormulaTemplateAccessEvaluator.canRead(authentication, #id)")
    public apiResponse<ScoreFormulaTemplateResponse> findById(@PathVariable String id) {
        return apiResponse.success("Lấy thông tin công thức tính điểm thành công", service.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@scoreFormulaTemplateAccessEvaluator.canUpdate(authentication, #id)")
    public apiResponse<ScoreFormulaTemplateResponse> update(@PathVariable String id, @Valid @RequestBody ScoreFormulaTemplateUpdateRequest request, Authentication authentication) {
        return apiResponse.success("Cập nhật công thức tính điểm thành công", service.update(id, request, authentication));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@scoreFormulaTemplateAccessEvaluator.canDelete(authentication, #id)")
    public apiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return apiResponse.success("Xóa công thức tính điểm thành công", null);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("@scoreFormulaTemplateAccessEvaluator.canUpdate(authentication, #id)")
    public apiResponse<ScoreFormulaTemplateResponse> toggleStatus(@PathVariable String id, @Valid @RequestBody StatusToggleRequest request, Authentication authentication) {
        return apiResponse.success("Đổi trạng thái công thức tính điểm thành công", service.toggleStatus(id, request, authentication));
    }
}
