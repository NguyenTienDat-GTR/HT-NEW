package phucuong.ht.modules.diocese.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.model.request.StatusToggleRequest;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.diocese.helper.DioceseValidationHelper;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.model.request.DioceseCreateRequest;
import phucuong.ht.modules.diocese.model.request.DioceseUpdateRequest;
import phucuong.ht.modules.diocese.model.response.DioceseResponse;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.diocese.security.DioceseAccessEvaluator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DioceseServiceTest {

    @Mock
    private DioceseRepository dioceseRepository;

    @Mock
    private DioceseValidationHelper dioceseValidationHelper;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private securityUtils securityUtils;

    @Mock
    private phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;

    @Mock
    private phucuong.ht.modules.auth.service.SiteHostPolicy siteHostPolicy;

    @org.junit.jupiter.api.BeforeEach
    void stubSiteBaseDomain() {
        // Mock trả null cho String; production luôn trả chuỗi (rỗng khi chưa cấu hình tên miền chính).
        org.mockito.Mockito.lenient().when(siteHostPolicy.baseDomain()).thenReturn("");
    }

    @InjectMocks
    private DioceseService dioceseService;

    @Test
    void createShouldValidateDelegateAndReturnResponseCounts() {
        DioceseCreateRequest request = new DioceseCreateRequest();
        request.setName("Phu Cuong");
        request.setUnionName("Phu Cuong");
        UUID id = UUID.randomUUID();
        Diocese created = sampleDiocese(id);
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(dioceseValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(dioceseRepository.save(argThat(entity ->
                "Phu Cuong".equals(entity.getName())
                        && "Phu Cuong".equals(entity.getUnionName())
                        && "LEADER1".equals(entity.getCreatedBy())
                        && "LEADER1".equals(entity.getUpdatedBy())
        ))).thenReturn(created);
        stubCounts(id, 2L, 5L, 20L);

        DioceseResponse result = dioceseService.create(request, authentication);

        assertEquals(id, result.getId());
        assertEquals("Sample Diocese", result.getName());
        assertEquals(2L, result.getDeaneryCount());
        assertEquals(5L, result.getParishCount());
        assertEquals(20L, result.getLeaderCount());
        verify(dioceseValidationHelper).validateCreateInput(request);
        verify(dioceseValidationHelper).validateCreateNameUniqueness("Phu Cuong");
    }

    @Test
    void findAllShouldDelegateToRepositoryForGlobalReadAndHydrateCounts() {
        UUID id = UUID.randomUUID();
        BaseSearchRequest request = new BaseSearchRequest();
        Page<Diocese> page = new PageImpl<>(List.of(sampleDiocese(id)));
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(dioceseRepository.findAll(any(BaseSearchRequest.class))).thenReturn(page);
        when(dioceseRepository.countDeaneriesByDioceseIds(List.of(id))).thenReturn(Map.of(id, 2L));
        when(dioceseRepository.countParishesByDioceseIds(List.of(id))).thenReturn(Map.of(id, 5L));
        when(dioceseRepository.countLeadersByDioceseIds(List.of(id))).thenReturn(Map.of(id, 20L));
        when(securityUtils.hasAuthority(authentication, DioceseAccessEvaluator.READ_ALL)).thenReturn(true);

        Page<DioceseResponse> result = dioceseService.findAll(request, authentication);

        assertEquals(id, result.getContent().getFirst().getId());
        assertEquals(2L, result.getContent().getFirst().getDeaneryCount());
        assertEquals(5L, result.getContent().getFirst().getParishCount());
        assertEquals(20L, result.getContent().getFirst().getLeaderCount());
        verify(dioceseRepository).findAll(argThat(criteria -> criteria.getFilters().isEmpty()));
    }

    @Test
    void findAllShouldApplyScopedDioceseFilter() {
        UUID dioceseId = UUID.randomUUID();
        UUID rowId = UUID.randomUUID();
        BaseSearchRequest request = new BaseSearchRequest();
        request.setFilters(new LinkedHashMap<>(Map.of("name", "Phu Cuong")));
        Page<Diocese> page = new PageImpl<>(List.of(sampleDiocese(rowId)));
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(securityUtils.hasAuthority(authentication, DioceseAccessEvaluator.READ_ALL)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, DioceseAccessEvaluator.READ_DIOCESE)).thenReturn(true);
        when(securityUtils.currentUsername(authentication)).thenReturn("admin-dio");
        when(accountRepository.findActiveDioceseIdByUsername("admin-dio")).thenReturn(Optional.of(dioceseId));
        when(dioceseRepository.findAll(any(BaseSearchRequest.class))).thenReturn(page);
        stubEmptyCounts(rowId);

        Page<DioceseResponse> result = dioceseService.findAll(request, authentication);

        assertEquals(rowId, result.getContent().getFirst().getId());
        verify(dioceseRepository).findAll(argThat(criteria ->
                dioceseId.equals(criteria.getFilters().get("id"))
                        && "Phu Cuong".equals(criteria.getFilters().get("name"))
        ));
    }

    @Test
    void findAllShouldRejectScopedReadWithoutResolvedDiocese() {
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(securityUtils.hasAuthority(authentication, DioceseAccessEvaluator.READ_ALL)).thenReturn(false);
        when(securityUtils.hasAuthority(authentication, DioceseAccessEvaluator.READ_DIOCESE)).thenReturn(true);
        when(securityUtils.currentUsername(authentication)).thenReturn("admin-dio");
        when(accountRepository.findActiveDioceseIdByUsername("admin-dio")).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> dioceseService.findAll(new BaseSearchRequest(), authentication));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void findByIdShouldLoadRequiredEntityFromHelperAndHydrateCounts() {
        UUID id = UUID.randomUUID();
        when(dioceseValidationHelper.requireExistingDiocese(id)).thenReturn(sampleDiocese(id));
        stubCounts(id, 1L, 3L, 9L);

        DioceseResponse result = dioceseService.findById(id, null);

        assertEquals(id, result.getId());
        assertEquals(1L, result.getDeaneryCount());
        assertEquals(3L, result.getParishCount());
        assertEquals(9L, result.getLeaderCount());
    }

    @Test
    void updateShouldValidateDelegateAndReturnResponse() {
        UUID id = UUID.randomUUID();
        Diocese existing = sampleDiocese(id);
        existing.setName("Old Name");
        DioceseUpdateRequest request = new DioceseUpdateRequest();
        request.setName("New Name");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(dioceseValidationHelper.requireExistingDiocese(id)).thenReturn(existing);
        when(dioceseValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(dioceseRepository.update(id, request, "LEADER1")).thenAnswer(invocation -> {
            existing.setName("New Name");
            return existing;
        });
        stubEmptyCounts(id);

        DioceseResponse result = dioceseService.update(id, request, authentication);

        assertEquals(id, result.getId());
        assertEquals("New Name", result.getName());
        verify(dioceseValidationHelper).validateUpdateInput(request);
        verify(dioceseValidationHelper).validateUpdateNameUniqueness(id, "New Name", "Old Name");
    }

    @Test
    void createShouldWrapDataConflict() {
        DioceseCreateRequest request = new DioceseCreateRequest();
        request.setName("Phu Cuong");
        request.setUnionName("Phu Cuong");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(dioceseValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(dioceseRepository.save(any(Diocese.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        ApiException exception = assertThrows(ApiException.class, () -> dioceseService.create(request, authentication));

        assertEquals("Dữ liệu giáo phận bị trùng hoặc xung đột với dữ liệu hiện có", exception.getMessage());
    }

    @Test
    void toggleStatusShouldValidateConfirmationAndDelegateToRepository() {
        UUID id = UUID.randomUUID();
        Diocese diocese = sampleDiocese(id);
        StatusToggleRequest request = new StatusToggleRequest();
        request.setConfirmationText("Xác nhận");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(dioceseValidationHelper.requireExistingDiocese(id)).thenReturn(diocese);
        when(dioceseValidationHelper.getRequiredAuditActor(authentication)).thenReturn("LEADER1");
        when(dioceseRepository.toggleStatus(id, "LEADER1")).thenReturn(diocese);
        stubEmptyCounts(id);

        DioceseResponse result = dioceseService.toggleStatus(id, request, authentication);

        assertEquals(id, result.getId());
        verify(dioceseValidationHelper).validateToggleConfirmation("Xác nhận");
        verify(dioceseRepository).toggleStatus(id, "LEADER1");
    }

    @Test
    void toggleStatusShouldPropagateConfirmationFailure() {
        UUID id = UUID.randomUUID();
        StatusToggleRequest request = new StatusToggleRequest();
        request.setConfirmationText("Sai");
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);

        when(dioceseValidationHelper.requireExistingDiocese(id)).thenReturn(new Diocese());
        doThrow(new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Không hợp lệ"))
                .when(dioceseValidationHelper)
                .validateToggleConfirmation("Sai");

        ApiException exception = assertThrows(ApiException.class, () -> dioceseService.toggleStatus(id, request, authentication));

        assertEquals("Không hợp lệ", exception.getMessage());
    }

    private Diocese sampleDiocese(UUID id) {
        Diocese diocese = new Diocese();
        diocese.setId(id);
        diocese.setName("Sample Diocese");
        diocese.setUnionName("Sample Union");
        return diocese;
    }

    private void stubEmptyCounts(UUID id) {
        stubCounts(id, 0L, 0L, 0L);
    }

    private void stubCounts(UUID id, long deaneries, long parishes, long leaders) {
        when(dioceseRepository.countDeaneriesByDioceseIds(List.of(id))).thenReturn(Map.of(id, deaneries));
        when(dioceseRepository.countParishesByDioceseIds(List.of(id))).thenReturn(Map.of(id, parishes));
        when(dioceseRepository.countLeadersByDioceseIds(List.of(id))).thenReturn(Map.of(id, leaders));
    }
}
