package phucuong.ht.modules.training.courseParticipation.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

@Component("courseParticipationAccessEvaluator")
@RequiredArgsConstructor
public class CourseParticipationAccessEvaluator {

    public static final String SUBMIT_PARISH = "training.participation.submit.parish";
    public static final String SUBMIT_DEANERY = "training.participation.submit.deanery";
    public static final String APPROVE_DEANERY = "training.participation.approve.deanery";
    public static final String APPROVE_DIOCESE = "training.participation.approve.diocese";
    public static final String SCORE_DEANERY = "training.participation.score.deanery";
    public static final String SCORE_DIOCESE = "training.participation.score.diocese";
    public static final String READ_DEANERY = "training.participation.read.deanery";
    public static final String READ_DIOCESE = "training.participation.read.diocese";

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final TrainingCourseRepository trainingCourseRepository;
    private final CourseParticipationRepository courseParticipationRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;

    public boolean canRegister(Authentication authentication, String courseCode) {
        return securityUtils.hasAuthority(authentication, SUBMIT_PARISH)
                && trainingCourseRepository.findByCodeWithRequirement(courseCode).isPresent();
    }

    public boolean canDeanerySubmit(Authentication authentication, String courseCode) {
        return securityUtils.hasAuthority(authentication, SUBMIT_DEANERY)
                && trainingCourseRepository.findByCodeWithRequirement(courseCode)
                .map(course -> trainingWorkflowSupport.isDeaneryManaged(course.getCourseType()) ? false : scopedDiocese(authentication, courseCode))
                .orElse(false);
    }

    public boolean canDeaneryApprove(Authentication authentication, String participationId) {
        return securityUtils.hasAuthority(authentication, APPROVE_DEANERY)
                && courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .map(participation -> trainingWorkflowSupport.isDeaneryManaged(participation.getCourse().getCourseType())
                        && scopedDeanery(authentication, participation.getCourse().getCourseCode()))
                .orElse(false);
    }

    public boolean canDioceseApprove(Authentication authentication, String participationId) {
        return securityUtils.hasAuthority(authentication, APPROVE_DIOCESE)
                && courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .map(participation -> !trainingWorkflowSupport.isDeaneryManaged(participation.getCourse().getCourseType())
                        && scopedDiocese(authentication, participation.getCourse().getCourseCode()))
                .orElse(false);
    }

    public boolean canScore(Authentication authentication, String participationId) {
        return courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .map(participation -> {
                    if (trainingWorkflowSupport.isDeaneryManaged(participation.getCourse().getCourseType())) {
                        return securityUtils.hasAuthority(authentication, SCORE_DEANERY)
                                && scopedDeanery(authentication, participation.getCourse().getCourseCode());
                    }
                    return securityUtils.hasAuthority(authentication, SCORE_DIOCESE)
                            && scopedDiocese(authentication, participation.getCourse().getCourseCode());
                })
                .orElse(false);
    }

    public boolean canRead(Authentication authentication) {
        return securityUtils.hasAuthority(authentication, READ_DEANERY)
                || securityUtils.hasAuthority(authentication, READ_DIOCESE)
                || securityUtils.hasAuthority(authentication, APPROVE_DEANERY)
                || securityUtils.hasAuthority(authentication, APPROVE_DIOCESE)
                || securityUtils.hasAuthority(authentication, SCORE_DEANERY)
                || securityUtils.hasAuthority(authentication, SCORE_DIOCESE);
    }

    public boolean canReadOne(Authentication authentication, String participationId) {
        return canRead(authentication) && courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .map(participation -> trainingWorkflowSupport.isDeaneryManaged(participation.getCourse().getCourseType())
                        ? scopedDeanery(authentication, participation.getCourse().getCourseCode())
                        : scopedDiocese(authentication, participation.getCourse().getCourseCode()))
                .orElse(false);
    }

    private boolean scopedDeanery(Authentication authentication, String courseCode) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }
        return accountRepository.findActiveDeaneryIdByUsername(username)
                .flatMap(activeId -> trainingCourseRepository.findDeaneryIdByCourseCode(courseCode).map(activeId::equals))
                .orElse(false);
    }

    private boolean scopedDiocese(Authentication authentication, String courseCode) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return false;
        }
        return accountRepository.findActiveDioceseIdByUsername(username)
                .flatMap(activeId -> trainingCourseRepository.findDioceseIdByCourseCode(courseCode).map(activeId::equals))
                .orElse(false);
    }
}
