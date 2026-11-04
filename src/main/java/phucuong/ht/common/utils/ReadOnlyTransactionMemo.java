package phucuong.ht.common.utils;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Ghi nhớ kết quả truy vấn trong phạm vi MỘT transaction chỉ đọc (readOnly). Khác {@link RequestMemo}, giá trị có thể là entity:
 * mọi lần dùng lại đều nằm trong cùng phiên Hibernate nên nạp lười vẫn hoạt động. Ngoài transaction chỉ đọc (transaction ghi,
 * không có transaction) luôn truy vấn lại, để không đọc nhầm dữ liệu vừa được sửa trong cùng transaction.
 */
public final class ReadOnlyTransactionMemo {

    private static final Object RESOURCE_KEY = new Object();
    private static final Object NULL = new Object();

    private ReadOnlyTransactionMemo() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Supplier<T> loader) {
        Map<String, Object> memo = memo(true);
        if (memo == null) {
            return loader.get();
        }
        Object cached = memo.get(key);
        if (cached != null) {
            return cached == NULL ? null : (T) cached;
        }
        T value = loader.get();
        memo.put(key, value == null ? NULL : value);
        return value;
    }

    /** Nạp sẵn kết quả (vd. đọc một lần cho cả trang thay vì mỗi dòng một câu). Không có transaction chỉ đọc thì bỏ qua. */
    public static void put(String key, Object value) {
        Map<String, Object> memo = memo(true);
        if (memo != null) {
            memo.put(key, value == null ? NULL : value);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> memo(boolean create) {
        if (!TransactionSynchronizationManager.isSynchronizationActive() || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return null;
        }
        Map<String, Object> memo = (Map<String, Object>) TransactionSynchronizationManager.getResource(RESOURCE_KEY);
        if (memo == null && create) {
            memo = new HashMap<>();
            TransactionSynchronizationManager.bindResource(RESOURCE_KEY, memo);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(RESOURCE_KEY);
                }
            });
        }
        return memo;
    }
}
