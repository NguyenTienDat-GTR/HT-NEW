package phucuong.ht.modules.position.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.position.model.entity.Position;

@Repository
public class PositionRepository extends BaseRepo<Position, String> {

    public PositionRepository(EntityManager entityManager) {
        super(entityManager, Position.class);
    }

    @Transactional
    public Position save(Position position) {
        entityManager.persist(position);
        return position;
    }
}
