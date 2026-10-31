package phucuong.ht.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.context.request.NativeWebRequest;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.exception.ApiException;

import java.beans.PropertyEditorSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class RequestBindingAdvice {

    private static final TypeReference<LinkedHashMap<String, Object>> FILTER_MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<LinkedHashMap<String, Object>>> FILTER_MAP_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    @InitBinder
    public void initBinder(WebDataBinder binder, NativeWebRequest request) {
        if (binder.getTarget() instanceof BaseSearchRequest searchRequest) {
            String filtersText = request.getParameter("filters");
            if (StringUtils.hasText(filtersText)) {
                try {
                    searchRequest.setFilters(parseFilters(filtersText));
                    binder.setDisallowedFields("filters");
                    return;
                } catch (Exception exception) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            errorCode.VALIDATION_ERROR,
                            "Định dạng filters không hợp lệ, vui lòng truyền chuỗi JSON object"
                    );
                }
            }
        }

        binder.registerCustomEditor(Map.class, "filters", new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (!StringUtils.hasText(text) || "null".equalsIgnoreCase(text) || "{}".equals(text.trim())) {
                    setValue(new LinkedHashMap<>());
                    return;
                }

                try {
                    setValue(parseFilters(text));
                } catch (Exception exception) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            errorCode.VALIDATION_ERROR,
                            "Định dạng filters không hợp lệ, vui lòng truyền chuỗi JSON object"
                    );
                }
            }
        });
    }

    private LinkedHashMap<String, Object> parseFilters(String text) throws java.io.IOException {
        String normalized = text.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            List<LinkedHashMap<String, Object>> filters = objectMapper.readValue(normalized, FILTER_MAP_LIST_TYPE);
            if (filters.isEmpty()) {
                return new LinkedHashMap<>();
            }
            if (filters.size() == 1) {
                return filters.get(0);
            }
            throw new IllegalArgumentException("filters chỉ ược truyền một JSON object");
        }
        return objectMapper.readValue(normalized, FILTER_MAP_TYPE);
    }
}
