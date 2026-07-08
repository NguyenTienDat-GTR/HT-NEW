package phucuong.ht.modules.system.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.config.AuditScopeContext;
import phucuong.ht.modules.system.model.entity.SystemLog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SystemLogRepository extends BaseRepo<SystemLog, String> {

    public SystemLogRepository(EntityManager entityManager) {
        super(entityManager, SystemLog.class);
    }

    @Transactional
    public SystemLog save(SystemLog systemLog) {
        entityManager.persist(systemLog);
        return systemLog;
    }

    public Optional<UUID> findDioceseIdByLogId(String id) {
        List<UUID> results = entityManager.createQuery(
                        "select sl.dioceseId from SystemLog sl where sl.id = :id",
                        UUID.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public Optional<UUID> findDeaneryIdByLogId(String id) {
        List<UUID> results = entityManager.createQuery(
                        "select sl.deaneryId from SystemLog sl where sl.id = :id",
                        UUID.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public Optional<UUID> findParishIdByLogId(String id) {
        List<UUID> results = entityManager.createQuery(
                        "select sl.parishId from SystemLog sl where sl.id = :id",
                        UUID.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public Optional<AuditScopeContext> findScopeById(String id) {
        return findById(id).map(log -> AuditScopeContext.builder()
                .unitType(log.getUnitType())
                .unitId(log.getUnitId())
                .dioceseId(log.getDioceseId())
                .deaneryId(log.getDeaneryId())
                .parishId(log.getParishId())
                .build());
    }
}
