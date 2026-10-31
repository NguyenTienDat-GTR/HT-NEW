package phucuong.ht.config.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.config.AuditScopeContext;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.executiveBoard.repository.ExecutiveBoardAssignmentRepository;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.util.UUID;

/** Cách xác định phạm vi cho từng loại tài nguyên được ghi log. */
@Configuration
public class AuditScopeSources {

    @Bean
    AuditScopeSource dioceseScope(DioceseRepository dioceses) {
        return AuditScopeSource.of("diocese", resourceId -> {
            UUID id = UUID.fromString(resourceId);
            if (dioceses.findById(id).isEmpty()) {
                return AuditScopeContext.empty();
            }
            return AuditScopeContext.builder().unitType(UnitType.DIOCESE).unitId(id).dioceseId(id).build();
        });
    }

    @Bean
    AuditScopeSource deaneryScope(DeaneryRepository deaneries) {
        return AuditScopeSource.of("deanery", resourceId -> {
            UUID id = UUID.fromString(resourceId);
            return AuditScopeContext.builder()
                    .unitType(UnitType.DEANERY)
                    .unitId(id)
                    .dioceseId(deaneries.findDioceseIdByDeaneryId(id).orElse(null))
                    .deaneryId(id)
                    .build();
        });
    }

    @Bean
    AuditScopeSource parishScope(ParishRepository parishes) {
        return AuditScopeSource.of("parish", resourceId -> parishScope(parishes, UUID.fromString(resourceId)));
    }

    @Bean
    AuditScopeSource leaderScope(LeaderRepository leaders, ParishRepository parishes) {
        return AuditScopeSource.of("leader", resourceId -> {
            UUID parishId = leaders.findParishIdByLeaderId(UUID.fromString(resourceId)).orElse(null);
            return parishId == null ? AuditScopeContext.empty() : parishScope(parishes, parishId);
        });
    }

    @Bean
    AuditScopeSource courseScope(TrainingCourseRepository courses) {
        return AuditScopeSource.of("course", resourceId -> trainingScope(
                courses.findDioceseIdByCourseCode(resourceId).orElse(null),
                courses.findDeaneryIdByCourseCode(resourceId).orElse(null)));
    }

    @Bean
    AuditScopeSource certificateScope(CertificateRepository certificates) {
        return AuditScopeSource.of("certificate", resourceId -> trainingScope(
                certificates.findDioceseIdByCertificateCode(resourceId).orElse(null),
                certificates.findDeaneryIdByCertificateCode(resourceId).orElse(null)));
    }

    @Bean
    AuditScopeSource executiveBoardAssignmentScope(ExecutiveBoardAssignmentRepository assignments) {
        return AuditScopeSource.of("executive_board_assignment",
                resourceId -> assignments.findScopeById(UUID.fromString(resourceId)).orElse(AuditScopeContext.empty()));
    }

    private static AuditScopeContext parishScope(ParishRepository parishes, UUID parishId) {
        return AuditScopeContext.builder()
                .unitType(UnitType.PARISH)
                .unitId(parishId)
                .dioceseId(parishes.findDioceseIdByParishId(parishId).orElse(null))
                .deaneryId(parishes.findDeaneryIdByParishId(parishId).orElse(null))
                .parishId(parishId)
                .build();
    }

    // Khóa/chứng nhận thuộc giáo hạt thì phạm vi là giáo hạt, ngược lại thuộc giáo phận.
    private static AuditScopeContext trainingScope(UUID dioceseId, UUID deaneryId) {
        if (deaneryId != null) {
            return AuditScopeContext.builder().unitType(UnitType.DEANERY).unitId(deaneryId).dioceseId(dioceseId).deaneryId(deaneryId).build();
        }
        if (dioceseId != null) {
            return AuditScopeContext.builder().unitType(UnitType.DIOCESE).unitId(dioceseId).dioceseId(dioceseId).build();
        }
        return AuditScopeContext.empty();
    }
}
