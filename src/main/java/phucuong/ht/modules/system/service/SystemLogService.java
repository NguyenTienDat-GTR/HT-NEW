package phucuong.ht.modules.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.system.model.entity.SystemLog;
import phucuong.ht.modules.system.model.response.SystemLogResponse;
import phucuong.ht.modules.system.repository.SystemLogRepository;
import phucuong.ht.modules.system.security.SystemLogAccessEvaluator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SystemLogService {

    private static final String DIOCESE_FILTER_FIELD = "dioceseId";
    private static final String DEANERY_FILTER_FIELD = "deaneryId";
    private static final String PARISH_FILTER_FIELD = "parishId";

    private final SystemLogRepository systemLogRepository;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final CommonValidationHelper commonValidationHelper;
    private final securityUtils securityUtils;

    public Page<SystemLogResponse> findAll(BaseSearchRequest request, Authentication authentication) {
        BaseSearchRequest scopedRequest = copyRequest(request);

        if (securityUtils.hasAuthority(authentication, SystemLogAccessEvaluator.READ_ALL)) {
            return map(systemLogRepository.findAll(scopedRequest));
        }

        if (securityUtils.hasAuthority(authentication, SystemLogAccessEvaluator.READ_DIOCESE)) {
            UUID dioceseId = accountScopeValidationHelper.requireActiveDioceseId(authentication, SystemLogAccessEvaluator.READ_DIOCESE);
            scopedRequest.getFilters().put(DIOCESE_FILTER_FIELD, dioceseId);
            return map(systemLogRepository.findAll(scopedRequest));
        }

        if (securityUtils.hasAuthority(authentication, SystemLogAccessEvaluator.READ_DEANERY)) {
            UUID deaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, SystemLogAccessEvaluator.READ_DEANERY);
            scopedRequest.getFilters().put(DEANERY_FILTER_FIELD, deaneryId);
            return map(systemLogRepository.findAll(scopedRequest));
        }

        if (securityUtils.hasAuthority(authentication, SystemLogAccessEvaluator.READ_PARISH)) {
            UUID parishId = accountScopeValidationHelper.requireActiveParishId(authentication, SystemLogAccessEvaluator.READ_PARISH);
            scopedRequest.getFilters().put(PARISH_FILTER_FIELD, parishId);
            return map(systemLogRepository.findAll(scopedRequest));
        }

        throw commonValidationHelper.forbidden("Tài khoản hiện tại không có quyền xem audit log");
    }

    public SystemLogResponse findById(String id) {
        return toResponse(systemLogRepository.findById(id)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy audit log " + id)));
    }

    private Page<SystemLogResponse> map(Page<SystemLog> page) {
        return new PageImpl<>(page.getContent().stream().map(this::toResponse).toList(), page.getPageable(), page.getTotalElements());
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

    private SystemLogResponse toResponse(SystemLog systemLog) {
        return SystemLogResponse.builder()
                .id(systemLog.getId())
                .username(systemLog.getUsername())
                .action(systemLog.getAction())
                .module(systemLog.getModule())
                .resource(systemLog.getResource())
                .resourceId(systemLog.getResourceId())
                .resourceName(systemLog.getResourceName())
                .operation(systemLog.getOperation())
                .queryString(systemLog.getQueryString())
                .durationMs(systemLog.getDurationMs())
                .httpMethod(systemLog.getHttpMethod())
                .requestPath(systemLog.getRequestPath())
                .statusCode(systemLog.getStatusCode())
                .ipAddress(systemLog.getIpAddress())
                .userAgent(systemLog.getUserAgent())
                .message(systemLog.getMessage())
                .errorMessage(systemLog.getErrorMessage())
                .loggedAt(systemLog.getLoggedAt())
                .unitType(systemLog.getUnitType())
                .unitId(systemLog.getUnitId())
                .dioceseId(systemLog.getDioceseId())
                .deaneryId(systemLog.getDeaneryId())
                .parishId(systemLog.getParishId())
                .build();
    }
}
