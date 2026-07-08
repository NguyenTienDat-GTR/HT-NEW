package phucuong.ht.modules.certificate.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.certificate.model.entity.Certificate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CertificateRepository extends BaseRepo<Certificate, String> {

    public CertificateRepository(EntityManager entityManager) {
        super(entityManager, Certificate.class);
    }

    @Transactional
    public Certificate save(Certificate certificate) {
        entityManager.persist(certificate);
        return certificate;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<Certificate> findByCodeWithLeaderAndCourse(String certificateCode) {
        List<Certificate> results = entityManager.createQuery(
                        """
                                select c
                                from Certificate c
                                left join fetch c.leader l
                                left join fetch l.parish p
                                left join fetch p.deanery de
                                left join fetch de.diocese d
                                left join fetch c.course tc
                                left join fetch tc.requirement
                                left join fetch tc.deanery
                                left join fetch tc.diocese
                                where c.certificateCode = :certificateCode
                                """,
                        Certificate.class
                )
                .setParameter("certificateCode", certificateCode)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public Optional<UUID> findDioceseIdByCertificateCode(String certificateCode) {
        List<UUID> results = entityManager.createQuery(
                        """
                                select coalesce(c.course.diocese.id, c.leader.parish.deanery.diocese.id)
                                from Certificate c
                                where c.certificateCode = :certificateCode
                                """,
                        UUID.class
                )
                .setParameter("certificateCode", certificateCode)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public Optional<UUID> findDeaneryIdByCertificateCode(String certificateCode) {
        List<UUID> results = entityManager.createQuery(
                        """
                                select coalesce(c.course.deanery.id, c.leader.parish.deanery.id)
                                from Certificate c
                                where c.certificateCode = :certificateCode
                                """,
                        UUID.class
                )
                .setParameter("certificateCode", certificateCode)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }
}
