package phucuong.ht.modules.certificate.config.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.certificate.config.model.entity.CertificateConfigAssignment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CertificateConfigAssignmentRepository extends BaseRepo<CertificateConfigAssignment, UUID> {

    public CertificateConfigAssignmentRepository(EntityManager entityManager) {
        super(entityManager, CertificateConfigAssignment.class);
    }

    @Transactional(readOnly = true)
    public List<CertificateConfigAssignment> findByScopeKeys(Collection<String> scopeKeys) {
        return entityManager.createQuery(
                        "select a from CertificateConfigAssignment a where a.scopeKey in :keys",
                        CertificateConfigAssignment.class)
                .setParameter("keys", scopeKeys)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public Optional<CertificateConfigAssignment> findByTypeAndScope(String courseTypeCode, String scopeKey) {
        return entityManager.createQuery(
                        "select a from CertificateConfigAssignment a where a.courseTypeCode = :code and a.scopeKey = :key",
                        CertificateConfigAssignment.class)
                .setParameter("code", courseTypeCode)
                .setParameter("key", scopeKey)
                .setMaxResults(1)
                .getResultList().stream().findFirst();
    }

    @Transactional
    public CertificateConfigAssignment persist(CertificateConfigAssignment assignment) {
        entityManager.persist(assignment);
        return assignment;
    }

    @Transactional
    public CertificateConfigAssignment merge(CertificateConfigAssignment assignment) {
        return entityManager.merge(assignment);
    }

    @Transactional
    public void remove(UUID id) {
        CertificateConfigAssignment managed = entityManager.find(CertificateConfigAssignment.class, id);
        if (managed != null) {
            entityManager.remove(managed);
        }
    }
}
