package phucuong.ht.modules.system.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.system.model.entity.ApiPermissionPolicy;

import java.util.UUID;

@Repository
public class ApiPermissionPolicyRepository extends BaseRepo<ApiPermissionPolicy, UUID> {

    public ApiPermissionPolicyRepository(EntityManager entityManager) {
        super(entityManager, ApiPermissionPolicy.class);
    }
}
