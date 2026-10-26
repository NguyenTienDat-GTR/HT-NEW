package phucuong.ht.modules.training.crossDeanery.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryItemStatus;
import phucuong.ht.modules.training.crossDeanery.model.CrossDeaneryStatus;
import phucuong.ht.modules.training.crossDeanery.model.entity.CrossDeaneryRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CrossDeaneryRequestRepository extends BaseRepo<CrossDeaneryRequest, String> {

    private static final int LIST_LIMIT = 300;

    public CrossDeaneryRequestRepository(EntityManager entityManager) {
        super(entityManager, CrossDeaneryRequest.class);
    }

    public CrossDeaneryRequest save(CrossDeaneryRequest request) {
        entityManager.persist(request);
        return request;
    }

    // Yêu cầu kèm khóa, giáo hạt tổ chức và huynh trưởng/giáo xứ của từng hồ sơ.
    public Optional<CrossDeaneryRequest> findByIdWithDetails(String id) {
        return entityManager.createQuery("""
                        select distinct r from CrossDeaneryRequest r
                        join fetch r.course c
                        left join fetch c.deanery
                        left join fetch r.items i
                        left join fetch i.leader l
                        left join fetch l.parish
                        where r.id = :id
                        """, CrossDeaneryRequest.class)
                .setParameter("id", id)
                .getResultList().stream().findFirst();
    }

    // Yêu cầu của một giáo hạt nhà (người đồng ý) hoặc của một giáo xứ (người gửi); status null = tất cả.
    public List<CrossDeaneryRequest> findByHomeDeanery(UUID homeDeaneryId, CrossDeaneryStatus status) {
        return query("r.homeDeaneryId = :scopeId", homeDeaneryId, status);
    }

    public List<CrossDeaneryRequest> findByParish(UUID parishId, CrossDeaneryStatus status) {
        return query("r.parishId = :scopeId", parishId, status);
    }

    private List<CrossDeaneryRequest> query(String scopeClause, UUID scopeId, CrossDeaneryStatus status) {
        // PENDING / EXPIRED được suy ra từ thời gian đăng ký của khóa nên lọc ở service; chỉ lọc sẵn các trạng thái đã lưu cuối cùng.
        boolean stored = status == CrossDeaneryStatus.APPROVED || status == CrossDeaneryStatus.REJECTED || status == CrossDeaneryStatus.CANCELLED;
        String statusClause = stored ? " and r.requestStatus = :status" : "";
        var query = entityManager.createQuery("""
                        select distinct r from CrossDeaneryRequest r
                        join fetch r.course c
                        left join fetch c.deanery
                        left join fetch r.items i
                        left join fetch i.leader l
                        left join fetch l.parish
                        where %s%s
                        order by r.createdAt desc
                        """.formatted(scopeClause, statusClause), CrossDeaneryRequest.class)
                .setParameter("scopeId", scopeId)
                .setMaxResults(LIST_LIMIT);
        if (!statusClause.isEmpty()) {
            query.setParameter("status", status);
        }
        return query.getResultList();
    }

    // Huynh trưởng đã nằm trong một yêu cầu đang chờ đồng ý cho khóa này.
    public boolean existsPendingItem(UUID leaderId, String courseCode) {
        Long count = entityManager.createQuery("""
                        select count(i) from CrossDeaneryRequestItem i
                        where i.leader.id = :leaderId and i.request.course.courseCode = :courseCode
                          and i.itemStatus = :status and i.request.requestStatus = :requestStatus
                        """, Long.class)
                .setParameter("leaderId", leaderId)
                .setParameter("courseCode", courseCode)
                .setParameter("status", CrossDeaneryItemStatus.PENDING)
                .setParameter("requestStatus", CrossDeaneryStatus.PENDING)
                .getSingleResult();
        return count != null && count > 0;
    }
}
