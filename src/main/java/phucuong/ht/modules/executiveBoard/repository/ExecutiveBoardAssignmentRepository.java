package phucuong.ht.modules.executiveBoard.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.config.AuditScopeContext;
import phucuong.ht.modules.executiveBoard.model.entity.ExecutiveBoardAssignment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ExecutiveBoardAssignmentRepository extends BaseRepo<ExecutiveBoardAssignment, UUID> {

    public ExecutiveBoardAssignmentRepository(EntityManager entityManager) {
        super(entityManager, ExecutiveBoardAssignment.class);
    }

    @Transactional
    public ExecutiveBoardAssignment save(ExecutiveBoardAssignment assignment) {
        entityManager.persist(assignment);
        return assignment;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<ExecutiveBoardAssignment> findByIdWithRelations(UUID id) {
        List<ExecutiveBoardAssignment> results = entityManager.createQuery(
                        """
                                select e
                                from ExecutiveBoardAssignment e
                                left join fetch e.position
                                left join fetch e.leader l
                                left join fetch l.parish p
                                left join fetch p.deanery pd
                                left join fetch pd.diocese
                                left join fetch e.diocese
                                left join fetch e.deanery
                                left join fetch e.parish
                                where e.id = :id
                                """,
                        ExecutiveBoardAssignment.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ExecutiveBoardAssignment> findAllByIdsWithRelations(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery(
                        """
                                select e
                                from ExecutiveBoardAssignment e
                                left join fetch e.position
                                left join fetch e.leader l
                                left join fetch l.parish p
                                left join fetch p.deanery pd
                                left join fetch pd.diocese
                                left join fetch e.diocese
                                left join fetch e.deanery
                                left join fetch e.parish
                                where e.id in :ids
                                """,
                        ExecutiveBoardAssignment.class
                )
                .setParameter("ids", ids)
                .getResultList();
    }

    public boolean existsActiveCurrentAssignment(UnitType unitType, UUID unitId, String positionCode, UUID excludedId) {
        Long count = entityManager.createQuery(
                        """
                                select count(e)
                                from ExecutiveBoardAssignment e
                                where e.unitType = :unitType
                                  and e.unitId = :unitId
                                  and e.position.positionCode = :positionCode
                                  and e.status = true
                                  and e.endDate is null
                                  and (:excludedId is null or e.id <> :excludedId)
                                """,
                        Long.class
                )
                .setParameter("unitType", unitType)
                .setParameter("unitId", unitId)
                .setParameter("positionCode", positionCode)
                .setParameter("excludedId", excludedId)
                .getSingleResult();
        return count != null && count > 0;
    }

    public Optional<AuditScopeContext> findScopeById(UUID id) {
        return findByIdWithRelations(id).map(assignment -> AuditScopeContext.builder()
                .unitType(assignment.getUnitType())
                .unitId(assignment.getUnitId())
                .dioceseId(assignment.getDiocese() == null ? null : assignment.getDiocese().getId())
                .deaneryId(assignment.getDeanery() == null ? null : assignment.getDeanery().getId())
                .parishId(assignment.getParish() == null ? null : assignment.getParish().getId())
                .build());
    }
}
