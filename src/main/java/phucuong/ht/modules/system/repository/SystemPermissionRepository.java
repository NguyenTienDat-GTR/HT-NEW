package phucuong.ht.modules.system.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.system.model.entity.Permission;

import java.util.List;
import java.util.Optional;

@Repository
public class SystemPermissionRepository extends BaseRepo<Permission, String> {

    public SystemPermissionRepository(EntityManager entityManager) {
        super(entityManager, Permission.class);
    }

    public Optional<String> findScopeCodeByPermissionCode(String permissionCode) {
        TypedQuery<String> query = entityManager.createQuery(
                "select p.scope.scopeCode from Permission p where p.permissionCode = :permissionCode",
                String.class
        );
        query.setParameter("permissionCode", permissionCode);
        query.setMaxResults(1);
        List<String> results = query.getResultList();
        return results.stream().findFirst();
    }
}
