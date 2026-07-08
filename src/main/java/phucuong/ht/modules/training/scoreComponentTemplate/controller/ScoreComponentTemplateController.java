package phucuong.ht.modules.training.scoreComponentTemplate.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import phucuong.ht.common.dto.apiResponse;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.training.scoreComponentTemplate.model.request.ScoreComponentTemplateCreateRequest;
import phucuong.ht.modules.training.scoreComponentTemplate.model.request.ScoreComponentTemplateUpdateRequest;
import phucuong.ht.modules.training.scoreComponentTemplate.model.response.ScoreComponentTemplateResponse;
import phucuong.ht.modules.training.scoreComponentTemplate.service.ScoreComponentTemplateService;

@RestController
@RequestMapping("/api/training/score-components")
@RequiredArgsConstructor
public class ScoreComponentTemplateController {

    private final ScoreComponentTemplateService service;

    @PostMapping
    @PreAuthorize("@scoreComponentTemplateAccessEvaluator.canCreate(authentication, #request.unitType)")
    public apiResponse<ScoreComponentTemplateResponse> create(@Valid @RequestBody ScoreComponentTemplateCreateRequest request, Authentication authentication) {
        return apiResponse.success("Create score component successfully", service.create(request, authentication));
    }

    @GetMapping
    @PreAuthorize("@scoreComponentTemplateAccessEvaluator.canList(authentication)")
    public apiResponse<Page<ScoreComponentTemplateResponse>> findAll(@ModelAttribute BaseSearchRequest request, Authentication authentication) {
        return apiResponse.success("Get score components successfully", service.findAll(request, authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@scoreComponentTemplateAccessEvaluator.canRead(authentication, #id)")
    public apiResponse<ScoreComponentTemplateResponse> findById(@PathVariable String id) {
        return apiResponse.success("Get score component successfully", service.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@scoreComponentTemplateAccessEvaluator.canUpdate(authentication, #id)")
    public apiResponse<ScoreComponentTemplateResponse> update(@PathVariable String id, @Valid @RequestBody ScoreComponentTemplateUpdateRequest request, Authentication authentication) {
        return apiResponse.success("Update score component successfully", service.update(id, request, authentication));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@scoreComponentTemplateAccessEvaluator.canDelete(authentication, #id)")
    public apiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return apiResponse.success("Delete score component successfully", null);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("@scoreComponentTemplateAccessEvaluator.canUpdate(authentication, #id)")
    public apiResponse<ScoreComponentTemplateResponse> toggleStatus(@PathVariable String id, @Valid @RequestBody StatusToggleRequest request, Authentication authentication) {
        return apiResponse.success("Toggle score component successfully", service.toggleStatus(id, request, authentication));
    }
}
