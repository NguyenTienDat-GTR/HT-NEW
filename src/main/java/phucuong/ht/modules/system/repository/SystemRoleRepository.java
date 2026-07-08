package phucuong.ht.modules.system.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.system.model.entity.Role;

import java.util.List;
import java.util.Optional;

@Repository
public class SystemRoleRepository extends BaseRepo<Role, String> {

    public SystemRoleRepository(EntityManager entityManager) {
        super(entityManager, Role.class);
    }

    public Optional<Integer> findDisplayOrderByRoleCode(String roleCode) {
        TypedQuery<Integer> query = entityManager.createQuery(
                "select r.displayOrder from Role r where r.roleCode = :roleCode",
                Integer.class
        );
        query.setParameter("roleCode", roleCode);
        query.setMaxResults(1);
        List<Integer> results = query.getResultList();
        return results.stream().findFirst();
    }
}
