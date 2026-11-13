package phucuong.ht.modules.training.registrationTemplate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.training.registrationTemplate.repository.RegistrationExportTemplateRepository;
import phucuong.ht.modules.training.registrationTemplate.security.RegistrationTemplateAccessEvaluator;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Phạm vi đơn vị của màn Mẫu danh sách đăng ký: tài khoản admin giáo hạt gắn qua huynh trưởng vẫn quản lý được mẫu của giáo hạt mình. */
class RegistrationTemplateServiceScopeTest {

    private static final String USERNAME = "admin.giaohat";

    private final UUID deaneryId = UUID.randomUUID();
    private AccountRepository accounts;
    private DeaneryRepository deaneries;
    private RegistrationExportTemplateRepository templates;
    private RegistrationTemplateAccessEvaluator access;
    private RegistrationTemplateService service;
    private final Authentication authentication = mock(Authentication.class);

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        deaneries = mock(DeaneryRepository.class);
        templates = mock(RegistrationExportTemplateRepository.class);
        access = mock(RegistrationTemplateAccessEvaluator.class);
        securityUtils security = mock(securityUtils.class);
        when(security.currentUsername(authentication)).thenReturn(USERNAME);
        when(access.isSuperAdmin(authentication)).thenReturn(false);
        when(accounts.findDirectActiveDioceseIdByUsername(USERNAME)).thenReturn(Optional.empty());
        when(accounts.findDirectActiveDeaneryIdByUsername(USERNAME)).thenReturn(Optional.empty());
        Deanery deanery = new Deanery();
        deanery.setName("Lái Thiêu");
        when(deaneries.findById(deaneryId)).thenReturn(Optional.of(deanery));
        when(templates.findByUnitTypeAndUnitId(any(), any())).thenReturn(Optional.empty());
        service = new RegistrationTemplateService(templates, mock(DioceseRepository.class), deaneries, accounts,
                mock(TrainingCourseRepository.class), access, security, null, null);
    }

    @Test
    void deaneryAdminLinkedThroughLeaderCanReadOwnDeaneryTemplate() {
        when(accounts.findActiveDeaneryIdByUsername(USERNAME)).thenReturn(Optional.of(deaneryId));
        when(access.has(authentication, "update", "deanery")).thenReturn(true);

        var response = service.get(null, null, authentication);

        assertEquals("DEANERY", response.unitType());
        assertEquals(deaneryId, response.unitId());
        assertEquals("Giáo hạt Lái Thiêu", response.unitName());
        assertFalse(response.configured());
    }

    @Test
    void stillRejectsAnotherDeaneryOrMissingPermission() {
        when(accounts.findActiveDeaneryIdByUsername(USERNAME)).thenReturn(Optional.of(deaneryId));
        when(access.has(authentication, "update", "deanery")).thenReturn(true);
        ApiException other = assertThrows(ApiException.class, () -> service.get("DEANERY", UUID.randomUUID(), authentication));
        assertEquals(HttpStatus.FORBIDDEN, other.getStatus());

        when(access.has(authentication, "update", "deanery")).thenReturn(false);
        assertThrows(ApiException.class, () -> service.get(null, null, authentication));
    }
}
