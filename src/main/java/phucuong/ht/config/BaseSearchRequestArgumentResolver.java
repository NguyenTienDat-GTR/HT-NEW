package phucuong.ht.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.modules.auth.exception.ApiException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BaseSearchRequestArgumentResolver implements HandlerMethodArgumentResolver {

    private static final TypeReference<LinkedHashMap<String, Object>> FILTER_MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<LinkedHashMap<String, Object>>> FILTER_MAP_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return BaseSearchRequest.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        BaseSearchRequest request = new BaseSearchRequest();
        setInt(webRequest, "page", request::setPage);
        setInt(webRequest, "size", request::setSize);
        request.setSearch(blankToNull(webRequest.getParameter("search")));
        request.setSearchFields(parseList(webRequest.getParameterValues("searchFields")));
        request.setFilters(parseFilters(webRequest.getParameter("filters")));
        request.setStatus(parseBoolean(webRequest.getParameter("status")));
        request.setSortBy(blankToNull(webRequest.getParameter("sortBy")));
        request.setSortDirection(blankToNull(webRequest.getParameter("sortDirection")));
        return request;
    }

    private void setInt(NativeWebRequest webRequest, String parameter, java.util.function.IntConsumer setter) {
        String value = webRequest.getParameter(parameter);
        if (!StringUtils.hasText(value)) {
            return;
        }
        try {
            setter.accept(Integer.parseInt(value.trim()));
        } catch (NumberFormatException exception) {
            throw validationError(parameter + " phải là số nguyên hợp lệ");
        }
    }

    private List<String> parseList(String[] values) {
        if (values == null || values.length == 0) {
            return List.of();
        }
        return Arrays.stream(values)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private Map<String, Object> parseFilters(String text) {
        if (!StringUtils.hasText(text) || "null".equalsIgnoreCase(text) || "{}".equals(text.trim())) {
            return new LinkedHashMap<>();
        }
        try {
            String normalized = text.trim();
            if (normalized.startsWith("[") && normalized.endsWith("]")) {
                List<LinkedHashMap<String, Object>> filters = objectMapper.readValue(normalized, FILTER_MAP_LIST_TYPE);
                if (filters.isEmpty()) {
                    return new LinkedHashMap<>();
                }
                if (filters.size() == 1) {
                    return filters.get(0);
                }
                throw validationError("filters chỉ được truyền một JSON object");
            }
            return objectMapper.readValue(normalized, FILTER_MAP_TYPE);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw validationError("Định dạng filters không hợp lệ, vui lòng truyền chuỗi JSON object");
        }
    }

    private Boolean parseBoolean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw validationError("status phải là true hoặc false");
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ApiException validationError(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, message);
    }
}
