package phucuong.ht.modules.position.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.position.model.entity.Position;
import phucuong.ht.modules.position.model.request.PositionCreateRequest;
import phucuong.ht.modules.position.model.request.PositionUpdateRequest;
import phucuong.ht.modules.position.model.response.PositionResponse;
import phucuong.ht.modules.position.repository.PositionRepository;

import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository positionRepository;
    private final CommonValidationHelper commonValidationHelper;

    public PositionResponse create(PositionCreateRequest request, Authentication authentication) {
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo chức vụ");
        Position position = new Position();
        position.setPositionName(commonValidationHelper.normalizeRequiredText(request.getPositionName()));
        position.setPositionType(request.getPositionType());
        position.setStatus(Boolean.TRUE);
        position.setCreatedBy(actor);
        position.setUpdatedBy(actor);
        positionRepository.save(position);
        return toResponse(position);
    }

    public Page<PositionResponse> findAll(BaseSearchRequest request) {
        BaseSearchRequest scopedRequest = copyRequest(request);
        Page<Position> page = positionRepository.findAll(scopedRequest);
        return new PageImpl<>(page.getContent().stream().map(this::toResponse).toList(), page.getPageable(), page.getTotalElements());
    }

    public PositionResponse findById(String positionCode) {
        return toResponse(positionRepository.findById(positionCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy chức vụ " + positionCode)));
    }

    public PositionResponse update(String positionCode, PositionUpdateRequest request, Authentication authentication) {
        Position position = positionRepository.findById(positionCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy chức vụ " + positionCode));
        if (request.getPositionName() != null) {
            position.setPositionName(commonValidationHelper.normalizeRequiredText(request.getPositionName()));
        }
        if (request.getPositionType() != null) {
            position.setPositionType(request.getPositionType());
        }
        position.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật chức vụ"));
        return toResponse(position);
    }

    public void delete(String positionCode) {
        positionRepository.delete(positionCode);
    }

    public PositionResponse toggleStatus(String positionCode, StatusToggleRequest request, Authentication authentication) {
        commonValidationHelper.requireStatusToggleConfirmation(request == null ? null : request.getConfirmationText());
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người đổi trạng thái chức vụ");
        positionRepository.toggleStatus(positionCode, actor);
        return findById(positionCode);
    }

    private BaseSearchRequest copyRequest(BaseSearchRequest request) {
        BaseSearchRequest source = request == null ? new BaseSearchRequest() : request;
        BaseSearchRequest copy = new BaseSearchRequest();
        copy.setPage(source.getPage());
        copy.setSize(source.getSize());
        copy.setSearch(source.getSearch());
        copy.setSearchFields(source.getSearchFields() == null ? List.of() : List.copyOf(source.getSearchFields()));
        copy.setFilters(source.getFilters() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getFilters()));
        copy.setStatus(source.getStatus());
        copy.setSortBy(source.getSortBy());
        copy.setSortDirection(source.getSortDirection());
        return copy;
    }

    private PositionResponse toResponse(Position position) {
        return PositionResponse.builder()
                .positionCode(position.getPositionCode())
                .positionName(position.getPositionName())
                .positionType(position.getPositionType())
                .status(position.getStatus())
                .createdAt(position.getCreatedAt())
                .updatedAt(position.getUpdatedAt())
                .build();
    }
}
