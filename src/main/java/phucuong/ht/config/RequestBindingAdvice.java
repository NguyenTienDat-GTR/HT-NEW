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
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.auth.exception.ApiException;

import java.beans.PropertyEditorSupport;
import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class RequestBindingAdvice {

    private static final TypeReference<LinkedHashMap<String, Object>> FILTER_MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Map.class, "filters", new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (!StringUtils.hasText(text) || "null".equalsIgnoreCase(text) || "{}".equals(text.trim())) {
                    setValue(new LinkedHashMap<>());
                    return;
                }

                try {
                    setValue(objectMapper.readValue(text, FILTER_MAP_TYPE));
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
}
