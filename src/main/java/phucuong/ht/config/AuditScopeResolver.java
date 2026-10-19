package phucuong.ht.config;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.executiveBoard.repository.ExecutiveBoardAssignmentRepository;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;
import org.springframework.security.core.Authentication;

import java.util.UUID;

@Component
public class AuditScopeResolver {

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final DioceseRepository dioceseRepository;
    private final DeaneryRepository deaneryRepository;
    private final ParishRepository parishRepository;
    private final LeaderRepository leaderRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final CertificateRepository certificateRepository;
    private final ExecutiveBoardAssignmentRepository executiveBoardAssignmentRepository;

    public AuditScopeResolver(
            securityUtils securityUtils,
            AccountRepository accountRepository,
            DioceseRepository dioceseRepository,
            DeaneryRepository deaneryRepository,
            ParishRepository parishRepository,
            LeaderRepository leaderRepository,
            TrainingCourseRepository trainingCourseRepository,
            CertificateRepository certificateRepository,
            ExecutiveBoardAssignmentRepository executiveBoardAssignmentRepository
    ) {
        this.securityUtils = securityUtils;
        this.accountRepository = accountRepository;
        this.dioceseRepository = dioceseRepository;
        this.deaneryRepository = deaneryRepository;
        this.parishRepository = parishRepository;
        this.leaderRepository = leaderRepository;
        this.trainingCourseRepository = trainingCourseRepository;
        this.certificateRepository = certificateRepository;
        this.executiveBoardAssignmentRepository = executiveBoardAssignmentRepository;
    }

    public AuditScopeContext resolve(Authentication authentication, String requestPath, String resource, String resourceId) {
        AuditScopeContext actorScope = resolveFromAuthentication(authentication);
        AuditScopeContext resourceScope = resolveFromResource(resource, resourceId);
        return AuditScopeContext.builder()
                .unitType(resourceScope.getUnitType() != null ? resourceScope.getUnitType() : actorScope.getUnitType())
                .unitId(resourceScope.getUnitId() != null ? resourceScope.getUnitId() : actorScope.getUnitId())
                .dioceseId(resourceScope.getDioceseId() != null ? resourceScope.getDioceseId() : actorScope.getDioceseId())
                .deaneryId(resourceScope.getDeaneryId() != null ? resourceScope.getDeaneryId() : actorScope.getDeaneryId())
                .parishId(resourceScope.getParishId() != null ? resourceScope.getParishId() : actorScope.getParishId())
                .build();
    }

    public AuditScopeContext resolveFromAuthentication(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        if (!StringUtils.hasText(username)) {
            return empty();
        }

        UUID dioceseId = accountRepository.findActiveDioceseIdByUsername(username).orElse(null);
        UUID deaneryId = accountRepository.findActiveDeaneryIdByUsername(username).orElse(null);
        UUID parishId = accountRepository.findActiveParishIdByUsername(username).orElse(null);

        UnitType unitType = null;
        UUID unitId = null;
        if (parishId != null) {
            unitType = UnitType.PARISH;
            unitId = parishId;
        } else if (deaneryId != null) {
            unitType = UnitType.DEANERY;
            unitId = deaneryId;
        } else if (dioceseId != null) {
            unitType = UnitType.DIOCESE;
            unitId = dioceseId;
        }

        return AuditScopeContext.builder()
                .unitType(unitType)
                .unitId(unitId)
                .dioceseId(dioceseId)
                .deaneryId(deaneryId)
                .parishId(parishId)
                .build();
    }

    public AuditScopeContext resolveFromResource(String resource, String resourceId) {
        if (!StringUtils.hasText(resource) || !StringUtils.hasText(resourceId)) {
            return empty();
        }

        try {
            return switch (resource) {
                case "diocese" -> {
                    UUID id = UUID.fromString(resourceId);
                    if (dioceseRepository.findById(id).isEmpty()) {
                        yield empty();
                    }
                    yield AuditScopeContext.builder()
                            .unitType(UnitType.DIOCESE)
                            .unitId(id)
                            .dioceseId(id)
                            .build();
                }
                case "deanery" -> {
                    UUID id = UUID.fromString(resourceId);
                    yield AuditScopeContext.builder()
                            .unitType(UnitType.DEANERY)
                            .unitId(id)
                            .dioceseId(deaneryRepository.findDioceseIdByDeaneryId(id).orElse(null))
                            .deaneryId(id)
                            .build();
                }
                case "parish" -> {
                    UUID id = UUID.fromString(resourceId);
                    yield AuditScopeContext.builder()
                            .unitType(UnitType.PARISH)
                            .unitId(id)
                            .dioceseId(parishRepository.findDioceseIdByParishId(id).orElse(null))
                            .deaneryId(parishRepository.findDeaneryIdByParishId(id).orElse(null))
                            .parishId(id)
                            .build();
                }
                case "leader" -> {
                    UUID id = UUID.fromString(resourceId);
                    UUID parishId = leaderRepository.findParishIdByLeaderId(id).orElse(null);
                    yield parishId == null ? empty() : AuditScopeContext.builder()
                            .unitType(UnitType.PARISH)
                            .unitId(parishId)
                            .dioceseId(parishRepository.findDioceseIdByParishId(parishId).orElse(null))
                            .deaneryId(parishRepository.findDeaneryIdByParishId(parishId).orElse(null))
                            .parishId(parishId)
                            .build();
                }
                case "course" -> {
                    UUID deaneryId = trainingCourseRepository.findDeaneryIdByCourseCode(resourceId).orElse(null);
                    UUID dioceseId = trainingCourseRepository.findDioceseIdByCourseCode(resourceId).orElse(null);
                    yield buildTrainingScope(dioceseId, deaneryId);
                }
                case "certificate" -> {
                    UUID deaneryId = certificateRepository.findDeaneryIdByCertificateCode(resourceId).orElse(null);
                    UUID dioceseId = certificateRepository.findDioceseIdByCertificateCode(resourceId).orElse(null);
                    yield buildTrainingScope(dioceseId, deaneryId);
                }
                case "executive_board_assignment" -> {
                    UUID id = UUID.fromString(resourceId);
                    yield executiveBoardAssignmentRepository.findScopeById(id).orElse(empty());
                }
                default -> empty();
            };
        } catch (IllegalArgumentException exception) {
            return empty();
        }
    }

    private AuditScopeContext buildTrainingScope(UUID dioceseId, UUID deaneryId) {
        if (deaneryId != null) {
            return AuditScopeContext.builder()
                    .unitType(UnitType.DEANERY)
                    .unitId(deaneryId)
                    .dioceseId(dioceseId)
                    .deaneryId(deaneryId)
                    .build();
        }
        if (dioceseId != null) {
            return AuditScopeContext.builder()
                    .unitType(UnitType.DIOCESE)
                    .unitId(dioceseId)
                    .dioceseId(dioceseId)
                    .build();
        }
        return empty();
    }

    private AuditScopeContext empty() {
        return AuditScopeContext.builder().build();
    }
}
