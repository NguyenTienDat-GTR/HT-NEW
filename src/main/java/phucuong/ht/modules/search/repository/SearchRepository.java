package phucuong.ht.modules.search.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** Chạy native SQL chỉ đọc; mọi giá trị người dùng đi qua tham số có tên, không nối chuỗi. */
@Repository
@RequiredArgsConstructor
public class SearchRepository {
    private final EntityManager entityManager;

    public List<Object[]> rows(String sql, Map<String, Object> params, int limit) {
        Query query = entityManager.createNativeQuery(sql);
        params.forEach(query::setParameter);
        query.setMaxResults(limit);
        @SuppressWarnings("unchecked") List<Object[]> result = query.getResultList();
        return result;
    }
}
