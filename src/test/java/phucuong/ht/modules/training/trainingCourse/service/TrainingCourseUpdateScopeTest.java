package phucuong.ht.modules.training.trainingCourse.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.model.AuthUserDetails;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.courseType.repository.TrainingCourseTypeRepository;
import phucuong.ht.modules.training.enums.CourseOrganizingStatus;
import phucuong.ht.modules.training.enums.CourseRegistrationStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.model.request.TrainingCourseUpdateRequest;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;
import phucuong.ht.modules.training.trainingCourse.security.TrainingCourseAccessEvaluator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingCourseUpdateScopeTest {

    @Mock private phucuong.ht.modules.training.courseScoreComponent.repository.TrainingCourseScoreComponentRepository courseScoreComponentRepository;
    @Mock private phucuong.ht.modules.notification.service.TrainingNotificationRecipientResolver notificationRecipientResolver;
    @Mock private phucuong.ht.modules.auth.repository.AccountRepository accountRepository;
    @Mock private phucuong.ht.config.DomainEventPublisher domainEventPublisher;
    @Mock private phucuong.ht.modules.auth.repository.AccountRoleRepository accountRoleRepository;
    @Mock private TrainingCourseRepository trainingCourseRepository;
    @Mock private TrainingCourseTypeRepository trainingCourseTypeRepository;
    @Mock private CourseParticipationRepository courseParticipationRepository;
    @Mock private DioceseRepository dioceseRepository;
    @Mock private DeaneryRepository deaneryRepository;
    @Mock private AccountScopeValidationHelper accountScopeValidationHelper;

    @Test
    void deaneryUpdaterCannotConvertOwnDtCourseIntoDioceseCourseWithoutDioceseUpdateAuthority() {
        UUID deaneryId = UUID.randomUUID();
        TrainingCourse course = deaneryCourse("DT-1", deaneryId);
        var authentication = authentication("admin-deanery", "training.course.update.deanery");
        TrainingCourseUpdateRequest request = new TrainingCourseUpdateRequest();
        request.setCourseTypeCode("SMTL_I");

        when(trainingCourseRepository.findByCodeWithType("DT-1")).thenReturn(Optional.of(course));
        when(trainingCourseTypeRepository.findByCodeWithPrerequisite("SMTL_I"))
                .thenReturn(Optional.of(courseType("SMTL_I", UnitType.DIOCESE)));
        when(accountScopeValidationHelper.requireActiveDeaneryId(
                eq(authentication), eq(TrainingCourseAccessEvaluator.UPDATE_DEANERY)))
                .thenReturn(deaneryId);

        assertThrows(RuntimeException.class, () -> service().update("DT-1", request, authentication));
    }

    @Test
    void deaneryUpdaterCanStillUpdateAnOwnDtCourseWithoutChangingItsOwnerType() {
        UUID deaneryId = UUID.randomUUID();
        TrainingCourse course = deaneryCourse("DT-2", deaneryId);
        var authentication = authentication("admin-deanery", "training.course.update.deanery");
        TrainingCourseUpdateRequest request = new TrainingCourseUpdateRequest();
        request.setCourseName("Updated DT course");

        when(trainingCourseRepository.findByCodeWithType("DT-2")).thenReturn(Optional.of(course));
        when(accountScopeValidationHelper.requireActiveDeaneryId(eq(authentication), anyString())).thenReturn(deaneryId);
        when(deaneryRepository.getRequired(deaneryId)).thenReturn(course.getDeanery());

        var response = service().update("DT-2", request, authentication);

        assertEquals("Updated DT course", response.getCourseName());
        assertEquals("SMTL_DT", response.getCourseTypeCode());
        assertEquals(deaneryId, response.getDeaneryId());
    }

    @Test
    void lockedFieldsCannotChangeOnceCourseHasParticipations() {
        UUID deaneryId = UUID.randomUUID();
        TrainingCourse course = deaneryCourse("DT-L1", deaneryId);
        course.setHostYear(2026);
        course.setYearsActive(1);
        course.setPassingScore(java.math.BigDecimal.valueOf(5));
        var authentication = authentication("admin-deanery", "training.course.update.deanery");
        when(trainingCourseRepository.findByCodeWithType("DT-L1")).thenReturn(Optional.of(course));
        when(accountScopeValidationHelper.requireActiveDeaneryId(eq(authentication), anyString())).thenReturn(deaneryId);
        when(courseParticipationRepository.existsByCourseCode("DT-L1")).thenReturn(true);

        TrainingCourseUpdateRequest hostYear = new TrainingCourseUpdateRequest();
        hostYear.setHostYear(2027);
        assertThrows(RuntimeException.class, () -> service().update("DT-L1", hostYear, authentication));

        TrainingCourseUpdateRequest yearsActive = new TrainingCourseUpdateRequest();
        yearsActive.setYearsActive(3);
        assertThrows(RuntimeException.class, () -> service().update("DT-L1", yearsActive, authentication));

        TrainingCourseUpdateRequest passing = new TrainingCourseUpdateRequest();
        passing.setPassingScore(java.math.BigDecimal.valueOf(6));
        assertThrows(RuntimeException.class, () -> service().update("DT-L1", passing, authentication));
    }

    @Test
    void lockedFieldsAcceptUnchangedValuesAndOtherFieldsStayEditable() {
        UUID deaneryId = UUID.randomUUID();
        TrainingCourse course = deaneryCourse("DT-L2", deaneryId);
        course.setHostYear(2026);
        course.setYearsActive(1);
        course.setPassingScore(java.math.BigDecimal.valueOf(5));
        var authentication = authentication("admin-deanery", "training.course.update.deanery");
        when(trainingCourseRepository.findByCodeWithType("DT-L2")).thenReturn(Optional.of(course));
        when(accountScopeValidationHelper.requireActiveDeaneryId(eq(authentication), anyString())).thenReturn(deaneryId);
        when(deaneryRepository.getRequired(deaneryId)).thenReturn(course.getDeanery());
        when(courseParticipationRepository.existsByCourseCode("DT-L2")).thenReturn(true);

        TrainingCourseUpdateRequest request = new TrainingCourseUpdateRequest();
        request.setHostYear(2026);
        request.setYearsActive(1);
        request.setPassingScore(new java.math.BigDecimal("5.00"));
        request.setLocation("Địa điểm mới");

        var response = service().update("DT-L2", request, authentication);

        assertEquals("Địa điểm mới", response.getLocation());
        assertEquals(true, response.getHasParticipations());
    }

    @Test
    void registrationCanBeClosedAndReopenedWhileWindowIsRunningAndPublishesEvents() {
        TrainingCourse course = deaneryCourse("DT-3", UUID.randomUUID());
        course.setRegistrationStartAt(java.time.OffsetDateTime.now().minusDays(1));
        course.setRegistrationEndAt(java.time.OffsetDateTime.now().plusDays(1));
        course.setRegistrationStatus(CourseRegistrationStatus.OPEN);
        var authentication = authentication("admin-deanery", "training.course.update.deanery");
        when(trainingCourseRepository.findByCodeWithType("DT-3")).thenReturn(Optional.of(course));

        var request = new phucuong.ht.modules.training.trainingCourse.model.request.CourseRegistrationToggleRequest();
        request.setOpen(false);
        var closed = service().setRegistrationOpen("DT-3", request, authentication);
        assertEquals(CourseRegistrationStatus.CLOSED, closed.getRegistrationStatus());
        assertEquals(false, closed.getRegistrationOpen());
        request.setOpen(true);
        assertEquals(CourseRegistrationStatus.OPEN, service().setRegistrationOpen("DT-3", request, authentication).getRegistrationStatus());
        verify(domainEventPublisher, times(2)).publishAfterCommit(any());
    }

    @Test
    void registrationCannotBeToggledAfterWindowEndedOrBeforeItStarts() {
        TrainingCourse ended = deaneryCourse("DT-4", UUID.randomUUID());
        ended.setRegistrationStartAt(java.time.OffsetDateTime.now().minusDays(2));
        ended.setRegistrationEndAt(java.time.OffsetDateTime.now().minusMinutes(1));
        TrainingCourse upcoming = deaneryCourse("DT-6", UUID.randomUUID());
        upcoming.setRegistrationStartAt(java.time.OffsetDateTime.now().plusDays(1));
        upcoming.setRegistrationEndAt(java.time.OffsetDateTime.now().plusDays(3));
        var authentication = authentication("admin-deanery", "training.course.update.deanery");
        when(trainingCourseRepository.findByCodeWithType("DT-4")).thenReturn(Optional.of(ended));
        when(trainingCourseRepository.findByCodeWithType("DT-6")).thenReturn(Optional.of(upcoming));

        var request = new phucuong.ht.modules.training.trainingCourse.model.request.CourseRegistrationToggleRequest();
        request.setOpen(true);
        assertThrows(RuntimeException.class, () -> service().setRegistrationOpen("DT-4", request, authentication));
        assertThrows(RuntimeException.class, () -> service().setRegistrationOpen("DT-6", request, authentication));
        verify(domainEventPublisher, never()).publishAfterCommit(any());
    }

    @Test
    void schedulerAutoOpensAtStartAutoClosesAtEndAndMovesOrganizingStatus() {
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        TrainingCourse starting = deaneryCourse("DT-7", UUID.randomUUID());
        starting.setRegistrationStartAt(now.minusMinutes(5));
        starting.setRegistrationEndAt(now.plusDays(1));
        starting.setStartDate(TrainingCourseStatusSupport.today().minusDays(1));
        starting.setEndDate(TrainingCourseStatusSupport.today().plusDays(1));
        starting.setRegistrationStatus(CourseRegistrationStatus.NOT_STARTED);
        starting.setOrganizingStatus(CourseOrganizingStatus.UPCOMING);

        TrainingCourse expiring = deaneryCourse("DT-8", UUID.randomUUID());
        expiring.setRegistrationStartAt(now.minusDays(3));
        expiring.setRegistrationEndAt(now.minusMinutes(5));
        expiring.setStartDate(TrainingCourseStatusSupport.today().minusDays(5));
        expiring.setEndDate(TrainingCourseStatusSupport.today().minusDays(1));
        expiring.setRegistrationStatus(CourseRegistrationStatus.OPEN);
        expiring.setOrganizingStatus(CourseOrganizingStatus.ONGOING);

        TrainingCourse manuallyClosed = deaneryCourse("DT-9", UUID.randomUUID());
        manuallyClosed.setRegistrationStartAt(now.minusDays(1));
        manuallyClosed.setRegistrationEndAt(now.plusDays(1));
        manuallyClosed.setStartDate(TrainingCourseStatusSupport.today().plusDays(2));
        manuallyClosed.setEndDate(TrainingCourseStatusSupport.today().plusDays(3));
        manuallyClosed.setRegistrationStatus(CourseRegistrationStatus.CLOSED);
        manuallyClosed.setOrganizingStatus(CourseOrganizingStatus.UPCOMING);

        when(trainingCourseRepository.findCoursesWithOpenStatuses()).thenReturn(List.of(starting, expiring, manuallyClosed));

        assertEquals(2, service().refreshCourseStatuses());
        assertEquals(CourseRegistrationStatus.OPEN, starting.getRegistrationStatus());
        assertEquals(CourseOrganizingStatus.ONGOING, starting.getOrganizingStatus());
        assertEquals(CourseRegistrationStatus.ENDED, expiring.getRegistrationStatus());
        assertEquals(CourseOrganizingStatus.ENDED, expiring.getOrganizingStatus());
        assertEquals(CourseRegistrationStatus.CLOSED, manuallyClosed.getRegistrationStatus());
        verify(domainEventPublisher, times(2)).publishAfterCommit(any());
    }

    @Test
    void passingScoreMustNotExceedWeightedMaxOfIncludedComponents() {
        UUID deaneryId = UUID.randomUUID();
        TrainingCourse course = deaneryCourse("DT-5", deaneryId);
        var authentication = authentication("admin-deanery", "training.course.update.deanery");
        TrainingCourseUpdateRequest request = new TrainingCourseUpdateRequest();
        request.setPassingScore(java.math.BigDecimal.valueOf(9));

        // Hai thành phần tối đa 10 (hệ số 1) và 5 (hệ số 1, tính vào tổng kết), 20 không tính -> giới hạn 7.5
        when(trainingCourseRepository.findByCodeWithType("DT-5")).thenReturn(Optional.of(course));
        when(accountScopeValidationHelper.requireActiveDeaneryId(any(), eq(TrainingCourseAccessEvaluator.UPDATE_DEANERY))).thenReturn(deaneryId);
        when(courseParticipationRepository.existsByCourseCode("DT-5")).thenReturn(false);
        when(deaneryRepository.getRequired(deaneryId)).thenReturn(course.getDeanery());
        when(accountScopeValidationHelper.requireActiveDeaneryId(any(), eq(TrainingCourseAccessEvaluator.CREATE_DEANERY))).thenReturn(deaneryId);
        when(courseScoreComponentRepository.findByCourseCode("DT-5"))
                .thenReturn(List.of(courseComponent(10, true), courseComponent(5, true), courseComponent(20, false)));

        assertThrows(RuntimeException.class, () -> service().update("DT-5", request, authentication));
        request.setPassingScore(java.math.BigDecimal.valueOf(7.5));
        assertEquals(0, java.math.BigDecimal.valueOf(7.5).compareTo(service().update("DT-5", request, authentication).getPassingScore()));
    }

    private phucuong.ht.modules.training.courseScoreComponent.model.entity.TrainingCourseScoreComponent courseComponent(int max, boolean included) {
        var item = new phucuong.ht.modules.training.courseScoreComponent.model.entity.TrainingCourseScoreComponent();
        item.setId("c-" + max);
        item.setName("Thành phần " + max);
        item.setMaxScore(java.math.BigDecimal.valueOf(max));
        item.setCoefficient(java.math.BigDecimal.ONE);
        item.setIncludeInFinal(included);
        return item;
    }

    private TrainingCourseService service() {
        return new TrainingCourseService(
                trainingCourseRepository,
                trainingCourseTypeRepository,
                org.mockito.Mockito.mock(phucuong.ht.modules.training.courseType.security.CourseTypeDioceseGuard.class),
                courseParticipationRepository,
                dioceseRepository,
                deaneryRepository,
                new TrainingWorkflowSupport(),
                accountScopeValidationHelper,
                new phucuong.ht.common.security.AccountScopeResolver(new securityUtils(), accountRepository),
                org.mockito.Mockito.mock(phucuong.ht.modules.training.RegistrationSenderResolver.class),
                new CommonValidationHelper(new securityUtils()),
                new securityUtils(),
                new phucuong.ht.modules.training.courseScoreComponent.service.CourseScoreComponentService(courseScoreComponentRepository, new CommonValidationHelper(new securityUtils())),
                courseScoreComponentRepository,
                notificationRecipientResolver,
                accountRepository,
                domainEventPublisher,
                accountRoleRepository,
                org.mockito.Mockito.mock(phucuong.ht.modules.training.trainingCourse.service.CourseFinishService.class),
                org.mockito.Mockito.mock(phucuong.ht.modules.certificate.service.CertificateLifecycleService.class),
                org.mockito.Mockito.mock(phucuong.ht.modules.certificate.repository.CertificateRepository.class),
                org.mockito.Mockito.mock(phucuong.ht.modules.training.CourseActivityGuard.class),
                org.mockito.Mockito.mock(phucuong.ht.modules.leaderLevel.service.LeaderLevelService.class),
                org.mockito.Mockito.mock(phucuong.ht.modules.certificate.service.CertificateRealtimeNotifier.class)
        , org.mockito.Mockito.mock(phucuong.ht.common.utils.AuditActorNameResolver.class));
    }

    @Test
    void findAllMergesDeaneryCoursesWithOpenDioceseAndDeaneryCoursesForMultiRoleAccounts() {
        UUID dioceseId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        when(accountRepository.findActiveDioceseIdByUsername("multi-role")).thenReturn(Optional.of(dioceseId));
        when(accountRepository.findActiveDeaneryIdByUsername("multi-role")).thenReturn(Optional.of(deaneryId));
        when(trainingCourseRepository.findAll(any(phucuong.ht.common.repository.BaseSearchRequest.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        org.mockito.ArgumentCaptor<phucuong.ht.common.repository.BaseSearchRequest> captor =
                org.mockito.ArgumentCaptor.forClass(phucuong.ht.common.repository.BaseSearchRequest.class);

        service().findAll(new phucuong.ht.common.repository.BaseSearchRequest(),
                authentication("multi-role", TrainingCourseAccessEvaluator.READ_DEANERY, TrainingCourseAccessEvaluator.SUBMIT_PARISH));

        verify(trainingCourseRepository).findAll(captor.capture());
        java.util.Map<String, Object> ownDeaneryCourses = java.util.Map.of("deanery.id", deaneryId);
        java.util.Map<String, Object> openDioceseCourses = new java.util.LinkedHashMap<>();
        openDioceseCourses.put("diocese.id", dioceseId);
        openDioceseCourses.put("status", Boolean.TRUE);
        openDioceseCourses.put("registrationStatus", CourseRegistrationStatus.OPEN);
        java.util.Map<String, Object> openDeaneryCourses = new java.util.LinkedHashMap<>();
        openDeaneryCourses.put("deanery.id", deaneryId);
        openDeaneryCourses.put("status", Boolean.TRUE);
        openDeaneryCourses.put("registrationStatus", CourseRegistrationStatus.OPEN);
        assertEquals(List.of(ownDeaneryCourses, openDioceseCourses, openDeaneryCourses), captor.getValue().getScopeAnyOf());
    }

    @Test
    void findAllRejectsAccountsWithoutAnyCourseReadPermission() {
        assertThrows(RuntimeException.class, () -> service().findAll(new phucuong.ht.common.repository.BaseSearchRequest(),
                authentication("nobody", "training.certificate.read.deanery")));
    }

    private TrainingCourse dioceseCourse(String code, UUID dioceseId) {
        Diocese diocese = new Diocese();
        diocese.setId(dioceseId);
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode(code);
        course.setCourseName("Khóa giáo phận");
        course.setCourseType(courseType("REGULAR", UnitType.DIOCESE));
        course.setDiocese(diocese);
        course.setPassingScore(java.math.BigDecimal.valueOf(5));
        return course;
    }

    @Test
    void courseDetailShowsPassingScoreAndScoreComponentsOnlyToTheOwningUnit() {
        UUID dioceseId = UUID.randomUUID();
        TrainingCourse course = dioceseCourse("KH-GP", dioceseId);
        when(trainingCourseRepository.findByCodeWithType("KH-GP")).thenReturn(Optional.of(course));
        when(accountRepository.findActiveDioceseIdByUsername("diocese-admin")).thenReturn(Optional.of(dioceseId));

        var response = service().findById("KH-GP", authentication("diocese-admin", "training.course.read.diocese"));

        assertEquals(Boolean.TRUE, response.getViewerOwnsCourse());
        assertEquals(java.math.BigDecimal.valueOf(5), response.getPassingScore());
        org.junit.jupiter.api.Assertions.assertNotNull(response.getScoreComponents());
        assertEquals(UnitType.DIOCESE, response.getManagementUnitType());
    }

    @Test
    void courseDetailHidesPassingScoreAndScoreComponentsFromDeaneryAndParishAdminsOfTheSameDiocese() {
        UUID dioceseId = UUID.randomUUID();
        UUID deaneryId = UUID.randomUUID();
        TrainingCourse course = dioceseCourse("KH-GP", dioceseId);
        when(trainingCourseRepository.findByCodeWithType("KH-GP")).thenReturn(Optional.of(course));
        when(accountRepository.findActiveDeaneryIdByUsername("multi-role")).thenReturn(Optional.of(deaneryId));

        var response = service().findById("KH-GP", authentication("multi-role",
                "training.course.read.deanery", "training.course.update.deanery", "training.participation.submit.parish"));

        assertEquals(Boolean.FALSE, response.getViewerOwnsCourse());
        org.junit.jupiter.api.Assertions.assertNull(response.getPassingScore());
        org.junit.jupiter.api.Assertions.assertNull(response.getScoreComponents());
        assertEquals("Khóa giáo phận", response.getCourseName());
        assertEquals(UnitType.DIOCESE, response.getManagementUnitType());
    }

    @Test
    void courseDetailKeepsScoreInfoForTheOwningDeaneryOfADeaneryCourse() {
        UUID deaneryId = UUID.randomUUID();
        TrainingCourse course = deaneryCourse("KH-GH", deaneryId);
        course.setPassingScore(java.math.BigDecimal.valueOf(6));
        when(trainingCourseRepository.findByCodeWithType("KH-GH")).thenReturn(Optional.of(course));
        when(accountRepository.findActiveDeaneryIdByUsername("deanery-admin")).thenReturn(Optional.of(deaneryId));

        var response = service().findById("KH-GH", authentication("deanery-admin", "training.course.read.deanery"));

        assertEquals(Boolean.TRUE, response.getViewerOwnsCourse());
        assertEquals(java.math.BigDecimal.valueOf(6), response.getPassingScore());
        assertEquals(UnitType.DEANERY, response.getManagementUnitType());
    }

    private TrainingCourseType courseType(String code, UnitType managementUnitType) {
        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode(code);
        type.setTypeName(code);
        type.setManagementUnitType(managementUnitType);
        type.setStatus(Boolean.TRUE);
        return type;
    }

    private TrainingCourse deaneryCourse(String code, UUID deaneryId) {
        Diocese diocese = new Diocese();
        diocese.setId(UUID.randomUUID());
        Deanery deanery = new Deanery();
        deanery.setId(deaneryId);
        deanery.setDiocese(diocese);
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode(code);
        course.setCourseName("DT course");
        course.setCourseType(courseType("SMTL_DT", UnitType.DEANERY));
        course.setDeanery(deanery);
        return course;
    }

    private UsernamePasswordAuthenticationToken authentication(String username, String... authorities) {
        AuthUserDetails principal = AuthUserDetails.tokenPrincipal(username, null, List.of("ADMIN_DEANERY"), List.of(authorities));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
