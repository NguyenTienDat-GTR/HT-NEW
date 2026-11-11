package phucuong.ht.common.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Ghi nhớ kết quả tra cứu trong phạm vi MỘT request đọc (GET / HEAD / OPTIONS): cùng một câu hỏi (vd. đơn vị của tài khoản,
 * cấp vai trò của người xem) được nhiều bước trong request hỏi lại thì chỉ truy vấn DB một lần.
 * <p>
 * Request ghi và tác vụ nền (không có request) luôn tra cứu lại như cũ, để không đọc nhầm dữ liệu vừa được chính request sửa.
 * Chỉ dùng cho giá trị nhỏ, không phải entity (entity gắn với phiên Hibernate nên không dùng lại được giữa các transaction).
 */
public final class RequestMemo {

    private static final String PREFIX = RequestMemo.class.getName() + ".";
    private static final Object NULL = new Object();
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private RequestMemo() {
    }

    public static <T> T get(String key, Supplier<T> loader) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet) || !isRead(servlet.getRequest())) {
            return loader.get();
        }
        return remember(attributes, key, loader);
    }

    /**
     * Như {@link #get} nhưng ghi nhớ trong mọi request, kể cả request ghi. Chỉ dùng cho giá trị mà các request gọi tới nó
     * chắc chắn không sửa (vd. phạm vi tài khoản khi thao tác loại khóa); nếu không, request sẽ đọc lại giá trị cũ.
     */
    public static <T> T getInAnyRequest(String key, Supplier<T> loader) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            return loader.get();
        }
        return remember(attributes, key, loader);
    }

    @SuppressWarnings("unchecked")
    private static <T> T remember(RequestAttributes attributes, String key, Supplier<T> loader) {
        String name = PREFIX + key;
        Object cached = attributes.getAttribute(name, RequestAttributes.SCOPE_REQUEST);
        if (cached != null) {
            return cached == NULL ? null : (T) cached;
        }
        T value = loader.get();
        attributes.setAttribute(name, value == null ? NULL : value, RequestAttributes.SCOPE_REQUEST);
        return value;
    }

    private static boolean isRead(HttpServletRequest request) {
        return request != null && request.getMethod() != null && READ_METHODS.contains(request.getMethod().toUpperCase(java.util.Locale.ROOT));
    }
}
