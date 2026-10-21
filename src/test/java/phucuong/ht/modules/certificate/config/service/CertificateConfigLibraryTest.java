package phucuong.ht.modules.certificate.config.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.certificate.config.model.entity.CertificateConfig;
import phucuong.ht.modules.certificate.config.model.entity.CertificateConfigAssignment;
import phucuong.ht.modules.certificate.config.model.request.CertificateConfigAssignRequest;
import phucuong.ht.modules.certificate.config.model.request.CertificateConfigUpsertRequest;
import phucuong.ht.modules.certificate.config.model.response.CertificateConfigResponse;
import phucuong.ht.modules.certificate.config.repository.CertificateConfigAssignmentRepository;
import phucuong.ht.modules.certificate.config.repository.CertificateConfigRepository;
import phucuong.ht.modules.certificate.config.security.CertificateConfigScopeResolver;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.leader.model.enums.LeaderLevel;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CertificateConfigLibraryTest {

    private static final String SYSTEM = "SYSTEM";

    private CertificateConfigRepository configRepository;
    private CertificateConfigAssignmentRepository assignmentRepository;
    private TrainingCourseTypeRepository courseTypeRepository;
    private AccountRepository accountRepository;
    private CertificateConfigService service;

    private final Authentication superAdmin = new UsernamePasswordAuthenticationToken(
            "super-admin", null, List.of(() -> "ROLE_SUPER_ADMIN", () -> "training.certificate_config.update.diocese"));

    @BeforeEach
    void setUp() {
        configRepository = mock(CertificateConfigRepository.class);
        assignmentRepository = mock(CertificateConfigAssignmentRepository.class);
        courseTypeRepository = mock(TrainingCourseTypeRepository.class);
        accountRepository = mock(AccountRepository.class);
        securityUtils securityUtils = new securityUtils();
        CommonValidationHelper validationHelper = new CommonValidationHelper(securityUtils);
        CertificateConfigScopeResolver scopeResolver = new CertificateConfigScopeResolver(securityUtils, accountRepository, validationHelper);
        service = new CertificateConfigService(
                configRepository, assignmentRepository, scopeResolver, mock(CertificateRepository.class),
                courseTypeRepository, null, validationHelper, securityUtils);

        when(configRepository.persist(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(configRepository.merge(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CertificateConfig config(String name, String scopeKey, boolean isDefault) {
        CertificateConfig config = new CertificateConfig();
        config.setId(UUID.randomUUID());
        config.setName(name);
        config.setScopeKey(scopeKey);
        config.setIsDefault(isDefault);
        config.setStatus(Boolean.TRUE);
        return config;
    }

    private CertificateConfigUpsertRequest request(String name) {
        CertificateConfigUpsertRequest request = new CertificateConfigUpsertRequest();
        request.setName(name);
        return request;
    }

    private Authentication deaneryAdmin(UUID deaneryId, UUID dioceseId) {
        when(accountRepository.findActiveDeaneryIdByUsername("dean")).thenReturn(Optional.of(deaneryId));
        when(accountRepository.findActiveDioceseIdByUsername("dean")).thenReturn(Optional.of(dioceseId));
        return new UsernamePasswordAuthenticationToken(
                "dean", null, List.of(() -> "training.certificate_config.update.deanery", () -> "training.certificate_config.delete.deanery"));
    }

    @Test
    void duplicateNameGetsSmallestFreePositiveSuffix() {
        when(configRepository.findNamesInScope(SYSTEM)).thenReturn(List.of("Mẫu A", "mẫu a (1)"));
        when(configRepository.findDefault(SYSTEM)).thenReturn(Optional.of(config("Chung", SYSTEM, true)));

        CertificateConfigResponse response = service.create(request("Mẫu A"), superAdmin);

        assertEquals("Mẫu A (2)", response.getName());
        assertFalse(response.getIsDefault());
    }

    @Test
    void uniqueNameIsKeptAndFirstConfigOfScopeBecomesDefault() {
        when(configRepository.findNamesInScope(SYSTEM)).thenReturn(List.of());
        when(configRepository.findDefault(SYSTEM)).thenReturn(Optional.empty());

        CertificateConfigResponse response = service.create(request("Mẫu mới"), superAdmin);

        assertEquals("Mẫu mới", response.getName());
        assertTrue(response.getIsDefault());
    }

    @Test
    void renamingToOwnCurrentNameDoesNotAddSuffix() {
        CertificateConfig existing = config("Mẫu A", SYSTEM, false);
        when(configRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(configRepository.findNamesInScope(SYSTEM)).thenReturn(List.of("Mẫu A", "Chung"));

        CertificateConfigResponse response = service.update(existing.getId(), request("mẫu a"), superAdmin);

        assertEquals("mẫu a", response.getName());
    }

    @Test
    void defaultConfigCannotBeDeleted() {
        CertificateConfig defaultConfig = config("Chung", SYSTEM, true);
        when(configRepository.findById(defaultConfig.getId())).thenReturn(Optional.of(defaultConfig));

        ApiException error = assertThrows(ApiException.class, () -> service.delete(defaultConfig.getId(), superAdmin));

        assertTrue(error.getMessage().contains("cấu hình chung"));
        verify(configRepository, never()).remove(any());
    }

    @Test
    void nonDefaultConfigCanBeDeleted() {
        CertificateConfig other = config("Mẫu A", SYSTEM, false);
        when(configRepository.findById(other.getId())).thenReturn(Optional.of(other));

        service.delete(other.getId(), superAdmin);

        verify(configRepository).remove(other.getId());
    }

    @Test
    void settingDefaultClearsOldDefaultBeforeSettingNewOne() {
        CertificateConfig oldDefault = config("Chung", SYSTEM, true);
        CertificateConfig next = config("Mẫu A", SYSTEM, false);
        when(configRepository.findById(next.getId())).thenReturn(Optional.of(next));
        when(configRepository.findDefault(SYSTEM)).thenReturn(Optional.of(oldDefault));

        service.setDefault(next.getId(), superAdmin);

        assertFalse(oldDefault.getIsDefault());
        assertTrue(next.getIsDefault());
        InOrder order = inOrder(configRepository);
        order.verify(configRepository).merge(oldDefault);
        order.verify(configRepository).flush();
        order.verify(configRepository).merge(next);
    }

    @Test
    void deaneryAdminCanUseButNotEditOrDeleteDioceseConfig() {
        UUID deaneryId = UUID.randomUUID();
        UUID dioceseId = UUID.randomUUID();
        Authentication deanery = deaneryAdmin(deaneryId, dioceseId);
        CertificateConfig dioceseConfig = config("Của giáo phận", "DIOCESE:" + dioceseId, false);
        when(configRepository.findById(dioceseConfig.getId())).thenReturn(Optional.of(dioceseConfig));

        assertThrows(ApiException.class, () -> service.delete(dioceseConfig.getId(), deanery));
        assertThrows(ApiException.class, () -> service.update(dioceseConfig.getId(), request("Sửa"), deanery));
        verify(configRepository, never()).remove(any());
    }

    @Test
    void deaneryAdminCannotSeeAnotherDioceseConfig() {
        Authentication deanery = deaneryAdmin(UUID.randomUUID(), UUID.randomUUID());
        CertificateConfig foreign = config("Giáo phận khác", "DIOCESE:" + UUID.randomUUID(), false);
        when(configRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThrows(ApiException.class, () -> service.delete(foreign.getId(), deanery));
        verify(configRepository, never()).remove(any());
    }

    @Test
    void deaneryAdminCreatesConfigInOwnDeaneryScope() {
        UUID deaneryId = UUID.randomUUID();
        Authentication deanery = deaneryAdmin(deaneryId, UUID.randomUUID());
        String key = "DEANERY:" + deaneryId;
        when(configRepository.findNamesInScope(key)).thenReturn(List.of());
        when(configRepository.findDefault(key)).thenReturn(Optional.empty());

        CertificateConfigResponse response = service.create(request("Giáo hạt mẫu"), deanery);

        assertEquals("DEANERY", response.getScopeLevel());
        assertTrue(response.getEditable());
    }

    @Test
    void assignRejectsConfigOutsideVisibleScopes() {
        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode("T1");
        type.setResultingLevel(LeaderLevel.HT_I);
        when(courseTypeRepository.findByCodeWithPrerequisite("T1")).thenReturn(Optional.of(type));
        UUID deaneryId = UUID.randomUUID();
        Authentication deanery = deaneryAdmin(deaneryId, UUID.randomUUID());
        CertificateConfig foreign = config("Khác", "DIOCESE:" + UUID.randomUUID(), false);
        when(configRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        CertificateConfigAssignRequest assign = new CertificateConfigAssignRequest();
        assign.setCourseTypeCode("T1");
        assign.setConfigId(foreign.getId());

        assertThrows(ApiException.class, () -> service.assign(assign, deanery));
        verify(assignmentRepository, never()).persist(any());
    }

    @Test
    void assignStoresAssignmentInActorScopeAndNullClearsIt() {
        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode("T1");
        type.setResultingLevel(LeaderLevel.HT_I);
        when(courseTypeRepository.findByCodeWithPrerequisite("T1")).thenReturn(Optional.of(type));
        CertificateConfig target = config("Mẫu A", SYSTEM, false);
        when(configRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(assignmentRepository.findByTypeAndScope("T1", SYSTEM)).thenReturn(Optional.empty());
        CertificateConfigAssignRequest assign = new CertificateConfigAssignRequest();
        assign.setCourseTypeCode("T1");
        assign.setConfigId(target.getId());

        service.assign(assign, superAdmin);

        verify(assignmentRepository).persist(any(CertificateConfigAssignment.class));

        CertificateConfigAssignment saved = new CertificateConfigAssignment();
        saved.setId(UUID.randomUUID());
        when(assignmentRepository.findByTypeAndScope("T1", SYSTEM)).thenReturn(Optional.of(saved));
        assign.setConfigId(null);

        service.assign(assign, superAdmin);

        verify(assignmentRepository).remove(saved.getId());
    }

    @Test
    void usageListsAssignedTypesAndDefaultFallbackTypes() {
        CertificateConfig defaultConfig = config("Chung", SYSTEM, true);
        CertificateConfig special = config("Riêng", SYSTEM, false);
        when(configRepository.findAllConfigs()).thenReturn(List.of(defaultConfig, special));
        when(configRepository.findByScopeKeys(List.of(SYSTEM))).thenReturn(List.of(defaultConfig, special));
        CertificateConfigAssignment assignment = new CertificateConfigAssignment();
        assignment.setCourseTypeCode("T1");
        assignment.setScopeKey(SYSTEM);
        assignment.setConfigId(special.getId());
        when(assignmentRepository.findByScopeKeys(List.of(SYSTEM))).thenReturn(List.of(assignment));
        TrainingCourseType t1 = new TrainingCourseType();
        t1.setTypeCode("T1");
        t1.setTypeName("Khóa 1");
        TrainingCourseType t2 = new TrainingCourseType();
        t2.setTypeCode("T2");
        t2.setTypeName("Khóa 2");
        when(courseTypeRepository.findCertifiedTypes()).thenReturn(List.of(t1, t2));

        List<CertificateConfigResponse> result = service.options(superAdmin);

        CertificateConfigResponse chung = result.stream().filter(r -> r.getId().equals(defaultConfig.getId())).findFirst().orElseThrow();
        CertificateConfigResponse rieng = result.stream().filter(r -> r.getId().equals(special.getId())).findFirst().orElseThrow();
        assertEquals(1, chung.getUsedByCount());
        assertEquals("T2", chung.getUsedByCourseTypes().get(0).getTypeCode());
        assertEquals(1, rieng.getUsedByCount());
        assertEquals("T1", rieng.getUsedByCourseTypes().get(0).getTypeCode());
    }

    @Test
    void searchFiltersFoldsAccentsAndPaginates() {
        CertificateConfig defaultConfig = config("Cấu hình chung", SYSTEM, true);
        CertificateConfig a = config("Mẫu Huynh trưởng", SYSTEM, false);
        CertificateConfig b = config("Mẫu Dự trưởng", SYSTEM, false);
        b.setPaperSize("A5");
        when(configRepository.findAllConfigs()).thenReturn(List.of(defaultConfig, a, b));
        when(configRepository.findByScopeKeys(List.of(SYSTEM))).thenReturn(List.of(defaultConfig, a, b));
        CertificateConfigAssignment assignment = new CertificateConfigAssignment();
        assignment.setCourseTypeCode("T1");
        assignment.setScopeKey(SYSTEM);
        assignment.setConfigId(a.getId());
        when(assignmentRepository.findByScopeKeys(List.of(SYSTEM))).thenReturn(List.of(assignment));
        TrainingCourseType t1 = new TrainingCourseType();
        t1.setTypeCode("T1");
        t1.setTypeName("Khóa Huynh trưởng I");
        when(courseTypeRepository.findCertifiedTypes()).thenReturn(List.of(t1));

        var request = new phucuong.ht.common.repository.BaseSearchRequest();
        request.setSearch("du truong");
        assertEquals(1, service.search(request, superAdmin).getTotalElements());

        request = new phucuong.ht.common.repository.BaseSearchRequest();
        request.setFilters(new java.util.LinkedHashMap<>(java.util.Map.of("used", "true")));
        assertEquals(a.getName(), service.search(request, superAdmin).getContent().get(0).getName());

        request = new phucuong.ht.common.repository.BaseSearchRequest();
        request.setSize(2);
        var first = service.search(request, superAdmin);
        assertEquals(3, first.getTotalElements());
        assertEquals(2, first.getContent().size());
        assertTrue(first.getContent().get(0).getIsDefault());
        request.setPage(1);
        assertEquals(1, service.search(request, superAdmin).getContent().size());
        assertTrue(service.search(request, superAdmin).getContent().get(0).getFieldLayout() == null);
    }
}
