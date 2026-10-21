package phucuong.ht.modules.certificate.config.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.certificate.config.model.entity.CertificateConfig;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CertificateConfigRepository extends BaseRepo<CertificateConfig, UUID> {

    public CertificateConfigRepository(EntityManager entityManager) {
        super(entityManager, CertificateConfig.class);
    }

    @Transactional(readOnly = true)
    public List<CertificateConfig> findByScopeKeys(Collection<String> scopeKeys) {
        return entityManager.createQuery(
                        "select c from CertificateConfig c where c.scopeKey in :keys order by c.isDefault desc, lower(c.name)",
                        CertificateConfig.class)
                .setParameter("keys", scopeKeys)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public List<CertificateConfig> findAllConfigs() {
        return entityManager.createQuery(
                        "select c from CertificateConfig c order by c.scopeKey, c.isDefault desc, lower(c.name)",
                        CertificateConfig.class)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public Optional<CertificateConfig> findDefault(String scopeKey) {
        return entityManager.createQuery(
                        "select c from CertificateConfig c where c.scopeKey = :key and c.isDefault = true",
                        CertificateConfig.class)
                .setParameter("key", scopeKey)
                .setMaxResults(1)
                .getResultList().stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<String> findNamesInScope(String scopeKey) {
        return entityManager.createQuery(
                        "select c.name from CertificateConfig c where c.scopeKey = :key", String.class)
                .setParameter("key", scopeKey)
                .getResultList();
    }

    @Transactional
    public CertificateConfig persist(CertificateConfig config) {
        entityManager.persist(config);
        return config;
    }

    @Transactional
    public CertificateConfig merge(CertificateConfig config) {
        return entityManager.merge(config);
    }

    @Transactional
    public void flush() {
        entityManager.flush();
    }

    @Transactional
    public void remove(UUID id) {
        CertificateConfig managed = entityManager.find(CertificateConfig.class, id);
        if (managed != null) {
            entityManager.remove(managed);
        }
    }
}
