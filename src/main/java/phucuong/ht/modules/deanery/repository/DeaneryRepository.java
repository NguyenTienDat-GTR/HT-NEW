package phucuong.ht.modules.deanery.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DeaneryRepository extends BaseRepo<Deanery, UUID> {

    public DeaneryRepository(EntityManager entityManager) {
        super(entityManager, Deanery.class);
    }

    @Transactional
    public Deanery save(Deanery deanery) {
        entityManager.persist(deanery);
        return deanery;
    }

    // Số giáo hạt đang hoạt động (bản thân và giáo phận cha đều bật) của giáo phận.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public int countActiveByDioceseId(UUID dioceseId) {
        Long count = entityManager.createQuery(
                        """
                        select count(de)
                        from Deanery de
                        where de.diocese.id = :dioceseId and de.status = true and de.diocese.status = true
                        """,
                        Long.class
                )
                .setParameter("dioceseId", dioceseId)
                .getSingleResult();
        return count == null ? 0 : count.intValue();
    }

    // Mã các giáo hạt đang hoạt động của giáo phận, trừ một giáo hạt (thường là giáo hạt tổ chức khóa).
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<UUID> findActiveIdsByDioceseIdExcluding(UUID dioceseId, UUID excludeDeaneryId) {
        return entityManager.createQuery(
                        "select de.id from Deanery de where de.diocese.id = :dioceseId and de.id <> :excludeId and de.status = true",
                        UUID.class)
                .setParameter("dioceseId", dioceseId)
                .setParameter("excludeId", excludeDeaneryId)
                .getResultList();
    }

    public boolean existsByNameAndDioceseId(String name, UUID dioceseId) {
        Long count = entityManager.createQuery(
                        """
                        select count(de)
                        from Deanery de
                        where de.name = :name and de.diocese.id = :dioceseId
                        """,
                        Long.class
                )
                .setParameter("name", name)
                .setParameter("dioceseId", dioceseId)
                .getSingleResult();
        return count != null && count > 0;
    }

    public boolean existsByNameAndDioceseIdAndIdNot(String name, UUID dioceseId, UUID id) {
        Long count = entityManager.createQuery(
                        """
                        select count(de)
                        from Deanery de
                        where de.name = :name
                          and de.diocese.id = :dioceseId
                          and de.id <> :id
                        """,
                        Long.class
                )
                .setParameter("name", name)
                .setParameter("dioceseId", dioceseId)
                .setParameter("id", id)
                .getSingleResult();
        return count != null && count > 0;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<Deanery> findByIdWithDiocese(UUID id) {
        List<Deanery> results = entityManager.createQuery(
                        """
                        select de
                        from Deanery de
                        join fetch de.diocese d
                        where de.id = :id
                        """,
                        Deanery.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Deanery> findAllByIdsWithDiocese(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                        """
                        select de
                        from Deanery de
                        join fetch de.diocese d
                        where de.id in :ids
                        """,
                        Deanery.class
                )
                .setParameter("ids", ids)
                .getResultList();
    }

    /** Toàn bộ giáo hạt kèm giáo phận (dùng để đối chiếu tên giáo hạt khi import giáo xứ). */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Deanery> findAllWithDiocese() {
        return entityManager.createQuery(
                        "select de from Deanery de join fetch de.diocese d", Deanery.class)
                .getResultList();
    }

    /** Tên các giáo hạt đã có của một giáo phận (dùng để báo trùng khi import giáo hạt). */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<String> findNamesByDioceseId(UUID dioceseId) {
        return entityManager.createQuery(
                        "select de.name from Deanery de where de.diocese.id = :dioceseId", String.class)
                .setParameter("dioceseId", dioceseId)
                .getResultList();
    }

    public Optional<UUID> findDioceseIdByDeaneryId(UUID deaneryId) {
        List<UUID> results = entityManager.createQuery(
                        """
                        select de.diocese.id
                        from Deanery de
                        where de.id = :deaneryId
                        """,
                        UUID.class
                )
                .setParameter("deaneryId", deaneryId)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public Diocese getDioceseReference(UUID dioceseId) {
        return entityManager.getReference(Diocese.class, dioceseId);
    }

    public Map<UUID, Long> countParishesByDeaneryIds(List<UUID> ids) {
        return countByDeaneryIds(ids, """
                select p.deanery.id, count(p)
                from Parish p
                where p.deanery.id in :ids and p.unitType = phucuong.ht.modules.parish.model.enums.ParishUnitType.PARISH
                group by p.deanery.id
                """);
    }

    public Map<UUID, Long> countLeadersByDeaneryIds(List<UUID> ids) {
        return countByDeaneryIds(ids, """
                select l.parish.deanery.id, count(l)
                from Leader l
                where l.parish.deanery.id in :ids
                group by l.parish.deanery.id
                """);
    }

    private Map<UUID, Long> countByDeaneryIds(List<UUID> ids, String queryText) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }

        List<Object[]> rows = entityManager.createQuery(queryText, Object[].class)
                .setParameter("ids", ids)
                .getResultList();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }
}
