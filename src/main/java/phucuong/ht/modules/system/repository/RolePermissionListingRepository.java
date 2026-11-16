package phucuong.ht.modules.system.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.system.model.entity.RolePermissionListing;

/** Chỉ đọc: dùng bộ tìm kiếm/lọc/sắp xếp chung của BaseRepo trên view danh sách phân quyền vai trò. */
@Repository
public class RolePermissionListingRepository extends BaseRepo<RolePermissionListing, String> {

    public RolePermissionListingRepository(EntityManager entityManager) {
        super(entityManager, RolePermissionListing.class);
    }
}
