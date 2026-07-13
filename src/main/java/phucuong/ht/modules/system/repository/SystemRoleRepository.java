package phucuong.ht.modules.system.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.system.model.entity.Role;
import phucuong.ht.modules.system.model.request.RoleCreateRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class SystemRoleRepository extends BaseRepo<Role, String> {

    public SystemRoleRepository(EntityManager entityManager) {
        super(entityManager, Role.class);
    }

    @Transactional
    public Role createRole(RoleCreateRequest request, String actor) {
        Role role = new Role();
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setIsSystem(Boolean.FALSE);
        role.setDisplayOrder(request.getDisplayOrder());

        OffsetDateTime now = OffsetDateTime.now();
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        if (StringUtils.hasText(actor)) {
            role.setCreatedBy(actor);
            role.setUpdatedBy(actor);
        }
        role.setStatus(Boolean.TRUE);

        entityManager.persist(role);
        return role;
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
