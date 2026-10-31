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

    /** Thứ tự kế tiếp (cuối cùng) trong danh sách chức vụ của đơn vị. */
    public int nextDisplayOrder(UnitType positionType, java.util.UUID unitId) {
        Integer max = entityManager.createQuery("select max(p.displayOrder) from Position p where p.positionType = :type and p.unitId = :unitId", Integer.class)
                .setParameter("type", positionType)
                .setParameter("unitId", unitId)
                .getSingleResult();
        return max == null ? 1 : max + 1;
    }

    public java.util.Optional<java.util.UUID> deaneryIdOfParish(java.util.UUID parishId) {
        return entityManager.createQuery("select pa.deanery.id from Parish pa where pa.id = :id", java.util.UUID.class)
                .setParameter("id", parishId)
                .getResultStream()
                .findFirst();
    }

    @Transactional
    public Position save(Position position) {
        entityManager.persist(position);
        return position;
    }
}
