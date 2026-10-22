package phucuong.ht.modules.analytics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Chạy native SQL chỉ đọc; mọi giá trị người dùng đi qua tham số có tên, không nối chuỗi. */
@Repository @RequiredArgsConstructor
public class AnalyticsRepository {
    private final EntityManager entityManager;

    public long scalar(String sql, Map<String, Object> params) {
        Object value = query(sql, params).getSingleResult();
        return value == null ? 0L : ((Number) value).longValue();
    }

    public BigDecimal decimal(String sql, Map<String, Object> params) {
        Object value = query(sql, params).getSingleResult();
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    public List<Object[]> rows(String sql, Map<String, Object> params) {
        @SuppressWarnings("unchecked") List<Object[]> result = query(sql, params).getResultList();
        return result;
    }

    /** Truy vấn một dòng nhiều cột tổng hợp. */
    public Object[] row(String sql, Map<String, Object> params) {
        Object value = query(sql, params).getSingleResult();
        return value instanceof Object[] array ? array : new Object[]{value};
    }

    public List<Object[]> page(String sql, Map<String, Object> params, int offset, int limit) {
        Query query = query(sql, params);
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        @SuppressWarnings("unchecked") List<Object[]> result = query.getResultList();
        return result;
    }

    private Query query(String sql, Map<String, Object> params) {
        Query query = entityManager.createNativeQuery(sql);
        // Hibernate từ chối tham số không xuất hiện trong câu lệnh, nên chỉ gán những tham số mà câu này thực sự dùng.
        params.forEach((name, value) -> {
            if (java.util.regex.Pattern.compile(":" + name + "\\b").matcher(sql).find()) query.setParameter(name, value);
        });
        return query;
    }
}
