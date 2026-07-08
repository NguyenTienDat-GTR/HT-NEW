package phucuong.ht.modules.leader.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.leader.model.entity.Leader;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class LeaderRepository extends BaseRepo<Leader, UUID> {

    public LeaderRepository(EntityManager entityManager) {
        super(entityManager, Leader.class);
    }

    @Transactional
    public Leader save(Leader leader) {
        entityManager.persist(leader);
        return leader;
    }

    public boolean existsByEmail(String email) {
        Long count = entityManager.createQuery(
                        """
                                select count(l)
                                from Leader l
                                where l.email = :email
                                """,
                        Long.class
                )
                .setParameter("email", email)
                .getSingleResult();
        return count != null && count > 0;
    }

    public boolean existsByEmailAndIdNot(String email, UUID id) {
        Long count = entityManager.createQuery(
                        """
                                select count(l)
                                from Leader l
                                where l.email = :email
                                  and l.id <> :id
                                """,
                        Long.class
                )
                .setParameter("email", email)
                .setParameter("id", id)
                .getSingleResult();
        return count != null && count > 0;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<Leader> findByIdWithParish(UUID id) {
        List<Leader> results = entityManager.createQuery(
                        """
                                select l
                                from Leader l
                                join fetch l.parish p
                                where l.id = :id
                                """,
                        Leader.class
                )
                .setParameter("id", id)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Leader> findAllByIdsWithParish(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery(
                        """
                                select l
                                from Leader l
                                join fetch l.parish p
                                where l.id in :ids
                                """,
                        Leader.class
                )
                .setParameter("ids", ids)
                .getResultList();
    }

    public Optional<UUID> findParishIdByLeaderId(UUID leaderId) {
        List<UUID> results = entityManager.createQuery(
                        """
                                select l.parish.id
                                from Leader l
                                where l.id = :leaderId
                                """,
                        UUID.class
                )
                .setParameter("leaderId", leaderId)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }
}
