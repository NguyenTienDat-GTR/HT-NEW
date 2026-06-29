package phucuong.ht.modules.diocese.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.diocese.model.entity.Diocese;

import java.util.UUID;

@Repository
public class DioceseRepository extends BaseRepo<Diocese, UUID> {

    public DioceseRepository(EntityManager entityManager) {
        super(entityManager, Diocese.class);
    }

    @Transactional
    public Diocese save(Diocese diocese) {
        entityManager.persist(diocese);
        return diocese;
    }

    public boolean existsByName(String name) {
        return existsByField("name", name);
    }

    public boolean existsByNameAndIdNot(String name, UUID id) {
        return findOneByField("name", name)
                .map(diocese -> !diocese.getId().equals(id))
                .orElse(false);
    }
}
