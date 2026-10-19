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
                                left join fetch tc.courseType
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

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<Certificate> findByLeaderIdAndCourseCode(UUID leaderId, String courseCode) {
        List<Certificate> results = entityManager.createQuery(
                        """
                                select certificate
                                from Certificate certificate
                                left join fetch certificate.leader
                                left join fetch certificate.course course
                                where certificate.leader.id = :leaderId
                                  and course.courseCode = :courseCode
                                """,
                        Certificate.class
                )
                .setParameter("leaderId", leaderId)
                .setParameter("courseCode", courseCode)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    public Optional<Long> findHighestSequenceByCodePrefix(String codePrefix) {
        List<String> codes = entityManager.createQuery(
                        """
                                select certificate.certificateCode
                                from Certificate certificate
                                where certificate.certificateCode like :codePrefix
                                """,
                        String.class
                )
                .setParameter("codePrefix", codePrefix + "%")
                .getResultList();
        long highest = 0L;
        for (String code : codes) {
            if (code == null) {
                continue;
            }
            int lastDash = code.lastIndexOf('-');
            if (lastDash < 0 || lastDash >= code.length() - 1) {
                continue;
            }
            try {
                highest = Math.max(highest, Long.parseLong(code.substring(lastDash + 1)));
            } catch (NumberFormatException ignored) {
                // Ignore non-standard codes.
            }
        }
        return highest == 0L ? Optional.empty() : Optional.of(highest);
    }

    public void lockCodePrefix(String codePrefix) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtext(:codePrefix))")
                .setParameter("codePrefix", codePrefix)
                .getSingleResult();
    }
}
