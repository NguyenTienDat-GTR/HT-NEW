package phucuong.ht.modules.position.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.position.model.entity.Position;

@Repository
public class PositionRepository extends BaseRepo<Position, String> {

    public PositionRepository(EntityManager entityManager) {
        super(entityManager, Position.class);
    }

    /** Thứ tự kế tiếp (cuối cùng) trong cấp của giáo phận. */
    public int nextDisplayOrder(UnitType positionType, java.util.UUID dioceseId) {
        Integer max = entityManager.createQuery("select max(p.displayOrder) from Position p where p.positionType = :type and p.dioceseId = :dioceseId", Integer.class)
                .setParameter("type", positionType)
                .setParameter("dioceseId", dioceseId)
                .getSingleResult();
        return max == null ? 1 : max + 1;
    }

    @Transactional
    public Position save(Position position) {
        entityManager.persist(position);
        return position;
    }
}
