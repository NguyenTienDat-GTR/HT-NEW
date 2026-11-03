package phucuong.ht.modules.parish.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.parish.model.entity.Parish;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ParishRepository extends BaseRepo<Parish, UUID> {

    public ParishRepository(EntityManager entityManager) {
        super(entityManager, Parish.class);
    }

    @Transactional
    public Parish save(Parish parish) {
        entityManager.persist(parish);
        return parish;
    }

    // Số đơn vị cơ sở (giáo xứ, giáo họ, giáo điểm) đang hoạt động (bản thân, giáo hạt và giáo phận cha đều bật) của giáo hạt.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public int countActiveByDeaneryId(UUID deaneryId) {
        Long count = entityManager.createQuery(
                        """
                                select count(p)
                                from Parish p
                                where p.deanery.id = :deaneryId and p.status = true and p.deanery.status = true and p.deanery.diocese.status = true
                                """,
                        Long.class
                )
                .setParameter("deaneryId", deaneryId)
                .getSingleResult();
        return count == null ? 0 : count.intValue();
    }

    // Giáo phận của giáo xứ, tra bằng truy vấn để không phụ thuộc vào proxy lazy ngoài phiên.
    public Optional<UUID> findDioceseIdById(UUID parishId) {
        return entityManager.createQuery(
                        "select p.deanery.diocese.id from Parish p where p.id = :id", UUID.class)
                .setParameter("id", parishId)
                .getResultList()
                .stream()
                .findFirst();
    }

    public boolean existsByNameAndDeaneryId(String name, UUID deaneryId) {
        Long count = entityManager.createQuery(
                        """
                                select count(p)
                                from Parish p
                                where p.name = :name and p.deanery.id = :deaneryId
                                """,
                        Long.class
                )
                .setParameter("name", name)
                .setParameter("deaneryId", deaneryId)
                .getSingleResult();
        return count != null && count > 0;
    }

    public boolean existsByNameAndDeaneryIdAndIdNot(String name, UUID deaneryId, UUID id) {
        Long count = entityManager.createQuery(
                        """
                                select count(p)
                                from Parish p
                                where p.name = :name
                                  and p.deanery.id = :deaneryId
                                  and p.id <> :id
                                """,
                        Long.class
                )
                .setParameter("name", name)
                .setParameter("deaneryId", deaneryId)
                .setParameter("id", id)
                .getSingleResult();
        return count != null && count > 0;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<Parish> findByIdWithDeaneryAndDiocese(UUID id) {
        List<Parish> results = entityManager.createQuery(
                        """
                                select p
                                from Parish p
                                join fetch p.deanery de
                                join fetch de.diocese d
                                left join fetch p.parentParish pp
                                where p.id = :id
                                """,
                        Parish.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Parish> findAllByIdsWithDeaneryAndDiocese(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                        """
                                select p
                                from Parish p
                                join fetch p.deanery de
                                join fetch de.diocese d
                                left join fetch p.parentParish pp
                                where p.id in :ids
                                """,
                        Parish.class
                )
                .setParameter("ids", ids)
                .getResultList();
    }

    /** Toàn bộ giáo xứ kèm giáo hạt và giáo phận (dùng để đối chiếu tên giáo xứ khi import huynh trưởng). */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Parish> findAllWithDeaneryAndDiocese() {
        return entityManager.createQuery(
                        """
                                select p
                                from Parish p
                                join fetch p.deanery d
                                join fetch d.diocese
                                """,
                        Parish.class
                )
                .getResultList();
    }

    // Mã các giáo xứ đang hoạt động của một giáo hạt.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<UUID> findActiveIdsByDeaneryId(UUID deaneryId) {
        return entityManager.createQuery(
                        "select p.id from Parish p where p.deanery.id = :deaneryId and p.status = true", UUID.class)
                .setParameter("deaneryId", deaneryId)
                .getResultList();
    }

    // Loại đơn vị, tên, giáo hạt và giáo phận của một đơn vị cơ sở (dùng để hiển thị trong thông báo).
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<ParishPlace> findPlaceById(UUID parishId) {
        List<Object[]> rows = entityManager.createQuery(
                        """
                                select p.unitType, p.name, p.deanery.name, p.deanery.diocese.name
                                from Parish p
                                where p.id = :parishId
                                """,
                        Object[].class
                )
                .setParameter("parishId", parishId)
                .setMaxResults(1)
                .getResultList();
        return rows.stream().findFirst().map(row -> new ParishPlace(
                ((phucuong.ht.modules.parish.model.enums.ParishUnitType) row[0]).getLabel(),
                (String) row[1], (String) row[2], (String) row[3]));
    }

    public Optional<UUID> findDeaneryIdByParishId(UUID parishId) {
        List<UUID> results = entityManager.createQuery(
                        """
                                select p.deanery.id
                                from Parish p
                                where p.id = :parishId
                                """,
                        UUID.class
                )
                .setParameter("parishId", parishId)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public Optional<UUID> findDioceseIdByParishId(UUID parishId) {
        List<UUID> results = entityManager.createQuery(
                        """
                                select p.deanery.diocese.id
                                from Parish p
                                where p.id = :parishId
                                """,
                        UUID.class
                )
                .setParameter("parishId", parishId)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    @Transactional
    public void updateParent(UUID id, UUID parentParishId, String actor) {
        entityManager.createQuery(
                        """
                                update Parish p
                                set p.parentParish.id = :parentId, p.updatedAt = :now, p.updatedBy = :actor
                                where p.id = :id
                                """)
                .setParameter("parentId", parentParishId)
                .setParameter("now", java.time.OffsetDateTime.now())
                .setParameter("actor", actor)
                .setParameter("id", id)
                .executeUpdate();
    }

    @Transactional
    public void promoteToParish(UUID id, String actor) {
        entityManager.createQuery(
                        """
                                update Parish p
                                set p.unitType = phucuong.ht.modules.parish.model.enums.ParishUnitType.PARISH,
                                    p.parentParish = null, p.updatedAt = :now, p.updatedBy = :actor
                                where p.id = :id
                                """)
                .setParameter("now", java.time.OffsetDateTime.now())
                .setParameter("actor", actor)
                .setParameter("id", id)
                .executeUpdate();
    }

    /** Số giáo họ / giáo điểm trực thuộc từng giáo xứ. */
    public Map<UUID, Long> countSubUnitsByParentIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager.createQuery(
                        """
                                select p.parentParish.id, count(p)
                                from Parish p
                                where p.parentParish.id in :ids
                                group by p.parentParish.id
                                """,
                        Object[].class
                )
                .setParameter("ids", ids)
                .getResultList();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    public Map<UUID, Long> countLeadersByParishIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }

        List<Object[]> rows = entityManager.createQuery(
                        """
                                select l.parish.id, count(l)
                                from Leader l
                                where l.parish.id in :ids
                                group by l.parish.id
                                """,
                        Object[].class
                )
                .setParameter("ids", ids)
                .getResultList();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }
}
