package phucuong.ht.modules.training.courseType.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.courseType.model.request.TrainingCourseTypeUpdateRequest;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingCourseTypeServiceTest {

    @Mock
    private TrainingCourseTypeRepository trainingCourseTypeRepository;
    @Mock
    private TrainingCourseRepository trainingCourseRepository;
    @Mock
    private LeaderLevelService leaderLevelService;

    @Test
    void maxYearsAfterPrerequisiteCanBeSetAndClearedToUnlimited() {
        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode("SMKN_I");
        when(trainingCourseTypeRepository.findByCodeWithPrerequisite("SMKN_I")).thenReturn(Optional.of(type));
        var service = new TrainingCourseTypeService(trainingCourseTypeRepository, trainingCourseRepository,
                new CommonValidationHelper(new securityUtils()), leaderLevelService,
                org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal("admin", null, List.of("ADMIN_DIOCESE"), List.of());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        var setRequest = new TrainingCourseTypeUpdateRequest();
        setRequest.setMaxYearsAfterPrerequisite(3);
        assertEquals(3, service.update("SMKN_I", setRequest, authentication).getMaxYearsAfterPrerequisite());

        var clearRequest = new TrainingCourseTypeUpdateRequest();
        clearRequest.setClearMaxYearsAfterPrerequisite(true);
        assertNull(service.update("SMKN_I", clearRequest, authentication).getMaxYearsAfterPrerequisite());
    }
}
