package phucuong.ht.modules.diocese.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.diocese.model.entity.Diocese;

@Repository
public class DioceseRepository extends BaseRepo<Diocese, String> {

    public DioceseRepository(EntityManager entityManager) {
        super(entityManager, Diocese.class);
    }

    @Transactional
    public Diocese save(Diocese diocese) {
        entityManager.persist(diocese);
        return diocese;
    }

    public boolean existsByDioceseCode(String dioceseCode) {
        return findById(dioceseCode).isPresent();
    }
}
