package phucuong.ht.modules.diocese.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.diocese.model.entity.Diocese;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

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

    public Optional<Diocese> findBySiteSubdomain(String siteSubdomain) {
        return entityManager.createQuery(
                        "select d from Diocese d where lower(d.siteSubdomain) = :sub", Diocese.class)
                .setParameter("sub", siteSubdomain.toLowerCase())
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    public boolean existsBySiteSubdomainAndIdNot(String siteSubdomain, UUID id) {
        return findBySiteSubdomain(siteSubdomain)
                .map(diocese -> !diocese.getId().equals(id))
                .orElse(false);
    }

    @Transactional
    public Diocese updateSiteSubdomain(UUID id, String siteSubdomain, String auditActor) {
        Diocese diocese = entityManager.find(Diocese.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (diocese == null) {
            throw new IllegalArgumentException("Diocese not found: " + id);
        }
        diocese.setSiteSubdomain(siteSubdomain);
        diocese.setUpdatedBy(auditActor);
        diocese.setUpdatedAt(java.time.OffsetDateTime.now());
        entityManager.flush();
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

    public Optional<Diocese> findByIdForUpdate(UUID id) {
        return Optional.ofNullable(entityManager.find(Diocese.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    public Map<UUID, Long> countDeaneriesByDioceseIds(List<UUID> ids) {
        return countByDioceseIds(ids, """
                select de.diocese.id, count(de)
                from Deanery de
                where de.diocese.id in :ids
                group by de.diocese.id
                """);
    }

    public Map<UUID, Long> countParishesByDioceseIds(List<UUID> ids) {
        return countByDioceseIds(ids, """
                select p.deanery.diocese.id, count(p)
                from Parish p
                where p.deanery.diocese.id in :ids and p.unitType = phucuong.ht.modules.parish.model.enums.ParishUnitType.PARISH
                group by p.deanery.diocese.id
                """);
    }

    public Map<UUID, Long> countLeadersByDioceseIds(List<UUID> ids) {
        return countByDioceseIds(ids, """
                select l.parish.deanery.diocese.id, count(l)
                from Leader l
                where l.parish.deanery.diocese.id in :ids
                group by l.parish.deanery.diocese.id
                """);
    }

    private Map<UUID, Long> countByDioceseIds(List<UUID> ids, String queryText) {
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
