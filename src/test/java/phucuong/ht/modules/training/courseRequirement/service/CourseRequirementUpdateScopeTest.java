package phucuong.ht.modules.training.courseRequirement.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseRequirement.model.entity.CourseRequirement;
import phucuong.ht.modules.training.courseRequirement.model.request.CourseRequirementUpdateRequest;
import phucuong.ht.modules.training.courseRequirement.repository.CourseRequirementRepository;
import phucuong.ht.modules.training.courseRequirement.security.CourseRequirementAccessEvaluator;
import phucuong.ht.modules.training.enums.CourseType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseRequirementUpdateScopeTest {

    @Mock private CourseRequirementRepository requirementRepository;
    @Mock private DioceseRepository dioceseRepository;
    @Mock private DeaneryRepository deaneryRepository;
    @Mock private AccountScopeValidationHelper accountScopeValidationHelper;

    @Test
    void deaneryUpdaterCannotConvertOwnDtRequirementToDioceseRequirementWithoutDioceseUpdateAuthority() {
        UUID deaneryId = UUID.randomUUID();
        CourseRequirement requirement = deaneryRequirement("REQ-1", deaneryId);
        var authentication = authentication("admin-deanery", "training.requirement.update.deanery");
        CourseRequirementUpdateRequest request = new CourseRequirementUpdateRequest();
        request.setCourseType(CourseType.SMTL_I);

        when(requirementRepository.findByCodeWithScope("REQ-1")).thenReturn(Optional.of(requirement));
        when(accountScopeValidationHelper.requireActiveDeaneryId(
                eq(authentication), eq(CourseRequirementAccessEvaluator.UPDATE_DEANERY)))
                .thenReturn(deaneryId);

        assertThrows(RuntimeException.class, () -> service().update("REQ-1", request, authentication));
    }

    @Test
    void deaneryUpdaterCanStillUpdateAnOwnDtRequirementWithoutChangingOwnerType() {
        UUID deaneryId = UUID.randomUUID();
        CourseRequirement requirement = deaneryRequirement("REQ-2", deaneryId);
        var authentication = authentication("admin-deanery", "training.requirement.update.deanery");
        CourseRequirementUpdateRequest request = new CourseRequirementUpdateRequest();
        request.setRequirementName("Updated requirement");

        when(requirementRepository.findByCodeWithScope("REQ-2")).thenReturn(Optional.of(requirement));
        when(accountScopeValidationHelper.requireActiveDeaneryId(eq(authentication), anyString())).thenReturn(deaneryId);
        when(deaneryRepository.getRequired(deaneryId)).thenReturn(requirement.getDeanery());

        var response = service().update("REQ-2", request, authentication);

        assertEquals("Updated requirement", response.getRequirementName());
        assertEquals(CourseType.SMTL_DT, response.getCourseType());
        assertEquals(deaneryId, response.getDeaneryId());
    }

    private CourseRequirementService service() {
        return new CourseRequirementService(
                requirementRepository,
                dioceseRepository,
                deaneryRepository,
                new TrainingWorkflowSupport(),
                accountScopeValidationHelper,
                new CommonValidationHelper(new securityUtils()),
                new securityUtils()
        );
    }

    private CourseRequirement deaneryRequirement(String code, UUID deaneryId) {
        Diocese diocese = new Diocese();
        diocese.setId(UUID.randomUUID());
        Deanery deanery = new Deanery();
        deanery.setId(deaneryId);
        deanery.setDiocese(diocese);
        CourseRequirement requirement = new CourseRequirement();
        requirement.setRequirementCode(code);
        requirement.setRequirementName("DT requirement");
        requirement.setCourseType(CourseType.SMTL_DT);
        requirement.setDeanery(deanery);
        return requirement;
    }

    private UsernamePasswordAuthenticationToken authentication(String username, String... authorities) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, null, List.of("ADMIN_DEANERY"), List.of(authorities));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
