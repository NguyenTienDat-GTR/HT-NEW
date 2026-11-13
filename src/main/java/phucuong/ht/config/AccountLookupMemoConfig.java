package phucuong.ht.config;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import phucuong.ht.common.utils.ReadOnlyTransactionMemo;
import phucuong.ht.common.utils.RequestMemo;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.auth.repository.AccountRoleRepository;
import phucuong.ht.modules.system.model.entity.AccountRole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Các câu tra cứu theo tên đăng nhập (đơn vị đang hoạt động, cấp vai trò, vai trò đang hiệu lực...) được gọi từ hàng chục chỗ
 * trong cùng một request: khóa cache theo phạm vi, kiểm tra quyền cho từng dòng, lọc theo phạm vi, dựng dữ liệu trả về.
 * Kết quả không đổi trong một lần đọc nên được ghi nhớ để mỗi câu chỉ chạy một lần; khi DB ở xa (mỗi câu vài chục ms)
 * việc này cắt phần lớn thời gian phản hồi. Request / transaction ghi vẫn truy vấn như cũ.
 * <ul>
 *     <li>{@link AccountRepository}: giá trị nhỏ (id, số, đúng/sai) nên ghi nhớ theo request đọc ({@link RequestMemo}).</li>
 *     <li>{@link AccountRoleRepository}: trả entity nên chỉ ghi nhớ trong một transaction chỉ đọc ({@link ReadOnlyTransactionMemo}).</li>
 * </ul>
 */
@Configuration
public class AccountLookupMemoConfig {

    private static final Set<String> ACCOUNT_LOOKUPS = Set.of(
            "findActiveDioceseIdByUsername",
            "findActiveDeaneryIdByUsername",
            "findActiveParishIdByUsername",
            "findHighestActiveRoleOrder",
            "existsByUsername"
    );
    private static final String ACTIVE_ROLES = "findActiveByUsername";
    private static final String ACTIVE_ROLE_CODES = "findActiveRoleCodesByUsername";

    @Bean
    static BeanPostProcessor accountLookupMemoPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof AccountRepository) {
                    return proxy(bean, AccountRepository.class, invocation -> {
                        String method = invocation.getMethod().getName();
                        return ACCOUNT_LOOKUPS.contains(method)
                                ? RequestMemo.get("account." + method + Arrays.toString(invocation.getArguments()), () -> proceed(invocation))
                                : invocation.proceed();
                    });
                }
                if (bean instanceof AccountRoleRepository) {
                    return proxy(bean, AccountRoleRepository.class, invocation -> {
                        String method = invocation.getMethod().getName();
                        if (!ACTIVE_ROLES.equals(method) && !ACTIVE_ROLE_CODES.equals(method)) {
                            return invocation.proceed();
                        }
                        List<?> cached = ReadOnlyTransactionMemo.get(roleKey(method, invocation.getArguments()[0]), () -> (List<?>) proceed(invocation));
                        // Trả bản sao: nơi gọi có thể sắp xếp / sửa danh sách mà không ảnh hưởng lần dùng sau.
                        return cached == null ? null : new ArrayList<>(cached);
                    });
                }
                return bean;
            }
        };
    }

    /**
     * Nạp sẵn vai trò đang hiệu lực của nhiều tài khoản bằng một câu, để các bước kiểm tra quyền / dựng dữ liệu cho từng tài khoản
     * dùng lại thay vì mỗi tài khoản vài câu. Chỉ có tác dụng trong transaction chỉ đọc.
     */
    public static void preloadActiveRoles(AccountRoleRepository repository, Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return;
        }
        Map<String, List<AccountRole>> byUsername = new LinkedHashMap<>();
        usernames.forEach(username -> byUsername.put(username, new ArrayList<>()));
        for (AccountRole accountRole : repository.findActiveByUsernameIn(usernames)) {
            byUsername.computeIfAbsent(accountRole.getAccount().getUsername(), key -> new ArrayList<>()).add(accountRole);
        }
        byUsername.forEach((username, roles) -> {
            ReadOnlyTransactionMemo.put(roleKey(ACTIVE_ROLES, username), roles);
            ReadOnlyTransactionMemo.put(roleKey(ACTIVE_ROLE_CODES, username), roles.stream().map(role -> role.getRole().getRoleCode()).toList());
        });
    }

    private static String roleKey(String method, Object username) {
        return "accountRole." + method + "[" + username + "]";
    }

    private static Object proxy(Object target, Class<?> type, MethodInterceptor interceptor) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addInterface(type);
        factory.addAdvice(interceptor);
        return factory.getProxy();
    }

    private static Object proceed(MethodInvocation invocation) {
        try {
            return invocation.proceed();
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException(throwable);
        }
    }
}
