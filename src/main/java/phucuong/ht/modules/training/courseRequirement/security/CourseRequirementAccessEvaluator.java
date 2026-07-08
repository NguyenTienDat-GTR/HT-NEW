package phucuong.ht.modules.training.courseRequirement.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseRequirement.repository.CourseRequirementRepository;
import phucuong.ht.modules.training.enums.CourseType;

@Component("courseRequirementAccessEvaluator")
@RequiredArgsConstructor
public class CourseRequirementAccessEvaluator {

    public static final String CREATE_DIOCESE = "training.requirement.create.diocese";
    public static final String READ_DIOCESE = "training.requirement.read.diocese";
    public static final String UPDATE_DIOCESE = "training.requirement.update.diocese";
    public static final String DELETE_DIOCESE = "training.requirement.delete.diocese";
    public static final String CREATE_DEANERY = "training.requirement.create.deanery";
    public static final String READ_DEANERY = "training.requirement.read.deanery";
    public static final String UPDATE_DEANERY = "training.requirement.update.deanery";
    public static final String DELETE_DEANERY = "training.requirement.delete.deanery";

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final CourseRequirementRepository courseRequirementRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;

    public boolean canCreate(Authentication authentication, CourseType courseType) {
        return trainingWorkflowSupport.isDeaneryManaged(courseType)
                ? securityUtils.hasAuthority(authentication, CREATE_DEANERY)
                : securityUtils.hasAuthority(authentication, CREATE_DIOCESE);
    }

    public boolean canList(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, READ_DIOCESE)
                || securityUtils.hasAuthority(authentication, READ_DEANERY);
    }

    public boolean canRead(Authentication authentication, String requirementCode) {
        return hasScopedAccess(authentication, requirementCode, READ_DIOCESE, READ_DEANERY);
    }

    public boolean canUpdate(Authentication authentication, String requirementCode) {
        return hasScopedAccess(authentication, requirementCode, UPDATE_DIOCESE, UPDATE_DEANERY);
    }

    public boolean canDelete(Authentication authentication, String requirementCode) {
        return hasScopedAccess(authentication, requirementCode, DELETE_DIOCESE, DELETE_DEANERY);
    }

    private boolean hasScopedAccess(Authentication authentication, String requirementCode, String dioceseAuthority, String deaneryAuthority) {
        if (requirementCode == null || requirementCode.isBlank()) {
            return false;
        }

        return courseRequirementRepository.findByCodeWithScope(requirementCode)
                .map(requirement -> {
                    String username = securityUtils.currentUsername(authentication);
                    if (username == null || username.isBlank()) {
                        return false;
                    }

                    if (trainingWorkflowSupport.isDeaneryManaged(requirement.getCourseType())) {
                        return securityUtils.hasAuthority(authentication, deaneryAuthority)
                                && accountRepository.findActiveDeaneryIdByUsername(username)
                                .flatMap(activeId -> courseRequirementRepository.findDeaneryIdByRequirementCode(requirementCode).map(activeId::equals))
                                .orElse(false);
                    }

                    return securityUtils.hasAuthority(authentication, dioceseAuthority)
                            && accountRepository.findActiveDioceseIdByUsername(username)
                            .flatMap(activeId -> courseRequirementRepository.findDioceseIdByRequirementCode(requirementCode).map(activeId::equals))
                            .orElse(false);
                })
                .orElse(false);
    }
}
