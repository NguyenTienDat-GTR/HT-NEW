package phucuong.ht.modules.certificate.config.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.certificate.config.model.entity.CertificateConfig;

import java.util.List;
import java.util.Optional;

@Repository
public class CertificateConfigRepository extends BaseRepo<CertificateConfig, java.util.UUID> {

    public CertificateConfigRepository(EntityManager entityManager) {
        super(entityManager, CertificateConfig.class);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<CertificateConfig> findAllConfigs() {
        return entityManager.createQuery(
                        "select c from CertificateConfig c order by c.courseTypeCode nulls first",
                        CertificateConfig.class)
                .getResultList();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<CertificateConfig> findByCourseTypeCode(String courseTypeCode) {
        return entityManager.createQuery(
                        "select c from CertificateConfig c where c.courseTypeCode = :code",
                        CertificateConfig.class)
                .setParameter("code", courseTypeCode)
                .setMaxResults(1)
                .getResultList()
                .stream().findFirst();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<CertificateConfig> findDefaultConfig() {
        return entityManager.createQuery(
                        "select c from CertificateConfig c where c.courseTypeCode is null",
                        CertificateConfig.class)
                .setMaxResults(1)
                .getResultList()
                .stream().findFirst();
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
    public void remove(java.util.UUID id) {
        CertificateConfig managed = entityManager.find(CertificateConfig.class, id);
        if (managed != null) {
            entityManager.remove(managed);
        }
    }
}
