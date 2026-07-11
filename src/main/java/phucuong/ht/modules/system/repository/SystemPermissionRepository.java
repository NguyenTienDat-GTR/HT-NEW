package phucuong.ht.modules.system.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.system.model.entity.PermissionAction;
import phucuong.ht.modules.system.model.entity.PermissionModule;
import phucuong.ht.modules.system.model.entity.PermissionResource;
import phucuong.ht.modules.system.model.entity.PermissionScope;
import phucuong.ht.modules.system.model.entity.Permission;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class SystemPermissionRepository extends BaseRepo<Permission, String> {

    public SystemPermissionRepository(EntityManager entityManager) {
        super(entityManager, Permission.class);
    }

    @Transactional
    public Permission createPermission(
            String permissionCode,
            String permissionName,
            String moduleCode,
            String resourceCode,
            String actionCode,
            String scopeCode,
            String description,
            Boolean isSystem,
            Integer displayOrder,
            String actor
    ) {
        Permission permission = new Permission();
        permission.setPermissionCode(permissionCode);
        permission.setPermissionName(permissionName);
        permission.setModule(entityManager.getReference(PermissionModule.class, moduleCode));
        permission.setResource(entityManager.getReference(PermissionResource.class, resourceCode));
        permission.setAction(entityManager.getReference(PermissionAction.class, actionCode));
        permission.setScope(entityManager.getReference(PermissionScope.class, scopeCode));
        permission.setDescription(description);
        permission.setIsSystem(isSystem);
        permission.setDisplayOrder(displayOrder);

        OffsetDateTime now = OffsetDateTime.now();
        permission.setCreatedAt(now);
        permission.setUpdatedAt(now);
        if (StringUtils.hasText(actor)) {
            permission.setCreatedBy(actor);
            permission.setUpdatedBy(actor);
        }
        permission.setStatus(Boolean.TRUE);

        entityManager.persist(permission);
        return permission;
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

    public boolean existsModuleCode(String moduleCode) {
        return entityManager.find(PermissionModule.class, moduleCode) != null;
    }

    public boolean existsActionCode(String actionCode) {
        return entityManager.find(PermissionAction.class, actionCode) != null;
    }

    public boolean existsScopeCode(String scopeCode) {
        return entityManager.find(PermissionScope.class, scopeCode) != null;
    }

    public boolean existsResourceCodeInModule(String resourceCode, String moduleCode) {
        TypedQuery<Long> query = entityManager.createQuery(
                """
                select count(pr)
                from PermissionResource pr
                where pr.resourceCode = :resourceCode
                  and pr.module.moduleCode = :moduleCode
                """,
                Long.class
        );
        query.setParameter("resourceCode", resourceCode);
        query.setParameter("moduleCode", moduleCode);
        return query.getSingleResult() > 0;
    }
}
