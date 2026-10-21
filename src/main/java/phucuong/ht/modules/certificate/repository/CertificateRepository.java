package phucuong.ht.modules.certificate.repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;
import phucuong.ht.common.repository.BaseRepo;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.certificate.model.enums.CertificateRevocationSource;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;

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

    @Transactional
    public void delete(Certificate certificate) {
        entityManager.remove(entityManager.contains(certificate) ? certificate : entityManager.merge(certificate));
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
        return results.stream().filter(java.util.Objects::nonNull).findFirst();
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
        return results.stream().filter(java.util.Objects::nonNull).findFirst();
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

    // Chứng nhận của khóa theo trạng thái, kèm huynh trưởng, giáo xứ - giáo hạt - giáo phận và loại khóa.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Certificate> findByCourseAndStatus(String courseCode, WorkflowApprovalStatus status) {
        return entityManager.createQuery(
                        """
                                select certificate
                                from Certificate certificate
                                left join fetch certificate.leader l
                                left join fetch l.parish p
                                left join fetch p.deanery de
                                left join fetch de.diocese
                                left join fetch certificate.course tc
                                left join fetch tc.courseType
                                where tc.courseCode = :courseCode
                                  and certificate.approvalStatus = :status
                                """,
                        Certificate.class
                )
                .setParameter("courseCode", courseCode)
                .setParameter("status", status)
                .getResultList();
    }

    // Chứng nhận của khóa bị thu hồi vì một nguồn cụ thể (ví dụ khóa bị vô hiệu hóa).
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Certificate> findRevokedByCourseAndSource(String courseCode, CertificateRevocationSource source) {
        return entityManager.createQuery(
                        """
                                select certificate
                                from Certificate certificate
                                left join fetch certificate.leader l
                                left join fetch l.parish p
                                left join fetch p.deanery de
                                left join fetch de.diocese
                                left join fetch certificate.course tc
                                left join fetch tc.courseType
                                where tc.courseCode = :courseCode
                                  and certificate.approvalStatus = :status
                                  and certificate.revocationSource = :source
                                """,
                        Certificate.class
                )
                .setParameter("courseCode", courseCode)
                .setParameter("status", WorkflowApprovalStatus.REVOKED)
                .setParameter("source", source)
                .getResultList();
    }

    // Mọi chứng nhận khác của một huynh trưởng (để kiểm tra thứ tự cấp khi thu hồi / cấp lại).
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Certificate> findOthersByLeader(UUID leaderId, String exceptCertificateCode) {
        return entityManager.createQuery(
                        """
                                select certificate
                                from Certificate certificate
                                left join fetch certificate.course tc
                                left join fetch tc.courseType
                                left join fetch tc.diocese
                                left join fetch tc.deanery
                                where certificate.leader.id = :leaderId
                                  and certificate.certificateCode <> :code
                                """,
                        Certificate.class
                )
                .setParameter("leaderId", leaderId)
                .setParameter("code", exceptCertificateCode)
                .getResultList();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public long countByCourseAndStatus(String courseCode, WorkflowApprovalStatus status) {
        Long count = entityManager.createQuery(
                        "select count(c) from Certificate c where c.course.courseCode = :courseCode and c.approvalStatus = :status",
                        Long.class)
                .setParameter("courseCode", courseCode)
                .setParameter("status", status)
                .getSingleResult();
        return count == null ? 0 : count;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public long countRevokedByCourseAndSource(String courseCode, CertificateRevocationSource source) {
        Long count = entityManager.createQuery(
                        "select count(c) from Certificate c where c.course.courseCode = :courseCode and c.approvalStatus = :status and c.revocationSource = :source",
                        Long.class)
                .setParameter("courseCode", courseCode)
                .setParameter("status", WorkflowApprovalStatus.REVOKED)
                .setParameter("source", source)
                .getSingleResult();
        return count == null ? 0 : count;
    }
}
