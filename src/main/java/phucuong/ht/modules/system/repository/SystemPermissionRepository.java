package phucuong.ht.modules.system.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.system.model.entity.PermissionAction;
import phucuong.ht.modules.system.model.entity.PermissionModule;
import phucuong.ht.modules.system.model.entity.PermissionResource;
import phucuong.ht.modules.system.model.entity.PermissionScope;
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

    public Permission save(Permission permission) {
        return entityManager.merge(permission);
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

    public List<PermissionModule> findActiveModules() {
        return entityManager.createQuery("select pm from PermissionModule pm where pm.status = true order by pm.displayOrder asc, pm.moduleCode", PermissionModule.class).getResultList();
    }

    public List<PermissionResource> findActiveResources() {
        return entityManager.createQuery("select pr from PermissionResource pr join fetch pr.module where pr.status = true order by pr.displayOrder asc, pr.resourceCode", PermissionResource.class).getResultList();
    }

    public List<PermissionAction> findActiveActions() {
        return entityManager.createQuery("select pa from PermissionAction pa where pa.status = true order by pa.displayOrder asc, pa.actionCode", PermissionAction.class).getResultList();
    }

    public List<PermissionScope> findActiveScopes() {
        return entityManager.createQuery("select ps from PermissionScope ps where ps.status = true order by ps.displayOrder asc, ps.scopeCode", PermissionScope.class).getResultList();
    }

    public List<Permission> findActivePermissions() {
        return entityManager.createQuery(
                """
                select p
                from Permission p
                left join fetch p.module
                left join fetch p.resource
                left join fetch p.action
                left join fetch p.scope
                where p.status = true
                order by p.displayOrder asc, p.permissionCode
                """,
                Permission.class
        ).getResultList();
    }

}
