package phucuong.ht.modules.training.courseCopy.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.notification.service.WorkflowDecisionNotifier.Decision;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.TrainingDecisionNotifier;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.Candidate;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.CopyRequest;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.CopyResult;
import phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.SourcesResponse;
import phucuong.ht.modules.training.courseInvitation.service.CourseInvitationAutoTrigger;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipationService;
import phucuong.ht.modules.training.courseType.model.entity.TrainingCourseType;
import phucuong.ht.modules.training.enums.CourseFinalResult;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseCopyServiceTest {

    private TrainingCourseRepository courseRepository;
    private CourseParticipationRepository participationRepository;
    private CourseParticipationService participationService;
    private TrainingDecisionNotifier notifier;
    private CourseInvitationAutoTrigger invitationTrigger;
    private CourseCopyService service;

    private final Diocese diocese = diocese();
    private final Deanery deanery = deanery("Phú Cường", diocese);
    private final Parish parish = parish("Búng", deanery);
    private final Parish otherDioceseParish = parish("Ngoài", deanery("Khác", diocese()));
    private final TrainingCourseType prerequisiteType = type("SMTL_I", null);
    private TrainingCourse target;

    @BeforeEach
    void setUp() {
        courseRepository = mock(TrainingCourseRepository.class);
        participationRepository = mock(CourseParticipationRepository.class);
        participationService = mock(CourseParticipationService.class);
        notifier = mock(TrainingDecisionNotifier.class);
        invitationTrigger = mock(CourseInvitationAutoTrigger.class);
        CommonValidationHelper validation = mock(CommonValidationHelper.class);
        when(validation.validationError(anyString())).thenAnswer(inv -> new ApiException(HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, inv.getArgument(0)));
        when(validation.requireAuditActor(any(), anyString())).thenReturn("admin");
        service = new CourseCopyService(courseRepository, participationRepository, participationService, new TrainingWorkflowSupport(),
                mock(CourseActivityGuard.class), validation);
        ReflectionTestUtils.setField(service, "trainingDecisionNotifier", notifier);
        ReflectionTestUtils.setField(service, "invitationAutoTrigger", invitationTrigger);
        securityUtils security = mock(securityUtils.class);
        when(security.hasAuthority(any(), anyString())).thenReturn(true);
        ReflectionTestUtils.setField(service, "securityUtils", security);

        target = course("TARGET", type("SMKN_I", prerequisiteType), null);
        target.setStartDate(java.time.LocalDate.now().plusDays(5));
        target.setEndDate(java.time.LocalDate.now().plusDays(10));
        when(courseRepository.findByCodeWithType("TARGET")).thenReturn(Optional.of(target));
        when(participationRepository.findLeaderIdsByCourseCode("TARGET")).thenReturn(Set.of());
        when(participationService.isEligible(any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void sourcesOnlyIncludeFinishedPrerequisiteCoursesInTheSameDiocese() {
        TrainingCourse own = course("A", prerequisiteType, OffsetDateTime.now());
        TrainingCourse foreign = course("B", prerequisiteType, OffsetDateTime.now());
        foreign.setDiocese(diocese());
        when(courseRepository.findFinishedByCourseTypeCode("SMTL_I")).thenReturn(List.of(own, foreign));
        Leader leader = leader("Hai", parish);
        when(participationRepository.findApprovedByCourseCodes(any())).thenReturn(List.of(participation(leader, own, CourseFinalResult.PASSED)));

        SourcesResponse response = service.sources("TARGET");

        assertTrue(response.available());
        assertEquals(1, response.sources().size());
        assertEquals("A", response.sources().get(0).courseCode());
        assertEquals(1, response.sources().get(0).passedCount());
    }

    @Test
    void noPrerequisiteMeansNoSources() {
        target.setCourseType(type("ROOT", null));
        SourcesResponse response = service.sources("TARGET");
        assertFalse(response.available());
        assertTrue(response.sources().isEmpty());
    }

    @Test
    void candidatesMergeDuplicatesPreferPassedAndRespectIncludeFailed() {
        TrainingCourse older = course("A", prerequisiteType, OffsetDateTime.now().minusYears(1));
        TrainingCourse newer = course("B", prerequisiteType, OffsetDateTime.now());
        when(courseRepository.findFinishedByCourseTypeCode("SMTL_I")).thenReturn(List.of(newer, older));
        Leader repeated = leader("Hai", parish);
        Leader failed = leader("Ba", parish);
        Leader outside = leader("Tu", otherDioceseParish);
        when(participationRepository.findApprovedByCourseCodes(anyCollection())).thenReturn(List.of(
                participation(repeated, newer, CourseFinalResult.FAILED),
                participation(repeated, older, CourseFinalResult.PASSED),
                participation(failed, newer, CourseFinalResult.FAILED),
                participation(outside, newer, CourseFinalResult.PASSED)));

        List<Candidate> passedOnly = service.candidates("TARGET", List.of("A", "B"), false);
        assertEquals(List.of(repeated.getId()), passedOnly.stream().map(Candidate::leaderId).toList());
        assertEquals("A", passedOnly.get(0).sourceCourseCode());

        List<Candidate> all = service.candidates("TARGET", List.of("A", "B"), true);
        assertEquals(2, all.size());
        assertEquals(CourseFinalResult.PASSED, all.stream().filter(item -> item.leaderId().equals(repeated.getId())).findFirst().orElseThrow().finalResult());
    }

    @Test
    void rejectsSourceCourseOutsideTheAllowedList() {
        when(courseRepository.findFinishedByCourseTypeCode("SMTL_I")).thenReturn(List.of(course("A", prerequisiteType, OffsetDateTime.now())));
        assertThrows(ApiException.class, () -> service.candidates("TARGET", List.of("X"), false));
    }

    @Test
    void copyCreatesApprovedParticipationsSkipsExistingAndNotifiesOriginalSubmitter() {
        TrainingCourse source = course("A", prerequisiteType, OffsetDateTime.now());
        when(courseRepository.findFinishedByCourseTypeCode("SMTL_I")).thenReturn(List.of(source));
        Leader fresh = leader("Hai", parish);
        Leader existing = leader("Ba", parish);
        CourseParticipation freshSource = participation(fresh, source, CourseFinalResult.PASSED);
        freshSource.setCreatedBy("parish-user");
        when(participationRepository.findApprovedByCourseCodes(anyCollection())).thenReturn(List.of(freshSource, participation(existing, source, CourseFinalResult.PASSED)));
        when(participationRepository.findLeaderIdsByCourseCode("TARGET")).thenReturn(Set.of(existing.getId()));
        List<CourseParticipation> saved = new ArrayList<>();
        when(participationRepository.save(any())).thenAnswer(inv -> {
            CourseParticipation item = inv.getArgument(0);
            item.setId("new-" + saved.size());
            saved.add(item);
            return item;
        });

        CopyResult result = service.copy("TARGET", new CopyRequest(List.of("A"), false, List.of(fresh.getId(), existing.getId())), mock(Authentication.class));

        assertEquals(1, result.copied());
        assertEquals(1, result.skipped().size());
        assertEquals(CourseCopyService.BLOCK_EXISTING, result.skipped().get(0).reason());
        CourseParticipation created = saved.get(0);
        assertEquals(WorkflowApprovalStatus.APPROVED, created.getApprovalStatus());
        assertEquals(ParticipationStatus.ATTENDED, created.getParticipationStatus());
        assertEquals(fresh, created.getLeader());
        assertNull(created.getTeamName());
        // Như duyệt đăng ký: bộ tự gửi email được gọi cho hồ sơ vừa sao chép (chỉ gửi khi khóa bật tự gửi).
        verify(invitationTrigger).afterApproved(List.of(created));
        verify(notifier).notifyRegistrations(eq(Decision.REGISTRATION_APPROVED), eq(List.of(created)), isNull(), eq("admin"),
                eq(Map.of("new-0", "parish-user")));
    }

    private TrainingCourse copyTarget(boolean autoSendInvitation) {
        TrainingCourse course = course("TARGET2", type("SMKN_I", prerequisiteType), null);
        course.setAutoSendInvitation(autoSendInvitation);
        course.setStartDate(java.time.LocalDate.now().plusDays(5));
        course.setEndDate(java.time.LocalDate.now().plusDays(10));
        return course;
    }

    private void stubSourceWith(TrainingCourse source, CourseParticipation... participations) {
        when(courseRepository.findFinishedByCourseTypeCode("SMTL_I")).thenReturn(List.of(source));
        when(participationRepository.findApprovedByCourseCodes(anyCollection())).thenReturn(List.of(participations));
        when(participationRepository.findLeaderIdsByCourseCode("TARGET2")).thenReturn(Set.of());
        when(participationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void copyFromSourcesCopiesOnlyThePickedLeadersAndTriggersAutoSendForTheCopiedOnes() {
        TrainingCourse source = course("A", prerequisiteType, OffsetDateTime.now());
        Leader one = leader("Hai", parish);
        Leader two = leader("Ba", parish);
        Leader three = leader("Tu", parish);
        stubSourceWith(source, participation(one, source, CourseFinalResult.PASSED), participation(two, source, CourseFinalResult.FAILED), participation(three, source, CourseFinalResult.PASSED));
        // Khóa đích bật tự gửi email: các hồ sơ vừa sao chép được giao cho bộ tự gửi (khóa không bật thì bộ này tự bỏ qua).
        TrainingCourse withAutoSend = copyTarget(true);

        CopyResult result = service.copyFromSources(withAutoSend, List.of("A"), true, List.of(one.getId(), two.getId()), mock(Authentication.class));

        // Chọn riêng thì bỏ qua cách "chỉ hồ sơ đạt": người không đạt được chọn vẫn sao chép, người không được chọn thì không.
        assertEquals(2, result.copied());
        org.mockito.ArgumentCaptor<CourseParticipation> saved = org.mockito.ArgumentCaptor.forClass(CourseParticipation.class);
        verify(participationRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertEquals(Set.of(one.getId(), two.getId()), saved.getAllValues().stream().map(item -> item.getLeader().getId()).collect(java.util.stream.Collectors.toSet()));
        org.mockito.ArgumentCaptor<java.util.Collection<CourseParticipation>> sent = org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        verify(invitationTrigger).afterApproved(sent.capture());
        assertEquals(2, sent.getValue().size());
        assertTrue(sent.getValue().stream().allMatch(item -> item.getCourse() == withAutoSend));
    }

    @Test
    void copyFromSourcesInPassedOnlyModeSkipsFailedLeadersWhenNobodyIsPicked() {
        TrainingCourse source = course("A", prerequisiteType, OffsetDateTime.now());
        Leader passed = leader("Hai", parish);
        Leader failed = leader("Ba", parish);
        stubSourceWith(source, participation(passed, source, CourseFinalResult.PASSED), participation(failed, source, CourseFinalResult.FAILED));

        CopyResult result = service.copyFromSources(copyTarget(false), List.of("A"), true, mock(Authentication.class));

        assertEquals(1, result.copied());
        org.mockito.ArgumentCaptor<CourseParticipation> saved = org.mockito.ArgumentCaptor.forClass(CourseParticipation.class);
        verify(participationRepository).save(saved.capture());
        assertEquals(passed.getId(), saved.getValue().getLeader().getId());
    }

    @Test
    void copyFromSourcesReportsPickedLeadersThatAreNotInTheSourceCourses() {
        TrainingCourse source = course("A", prerequisiteType, OffsetDateTime.now());
        Leader inSource = leader("Hai", parish);
        stubSourceWith(source, participation(inSource, source, CourseFinalResult.PASSED));
        UUID stranger = UUID.randomUUID();

        CopyResult result = service.copyFromSources(copyTarget(false), List.of("A"), false, List.of(inSource.getId(), stranger), mock(Authentication.class));

        assertEquals(1, result.copied());
        assertEquals(1, result.skipped().size());
        assertEquals(stranger, result.skipped().get(0).leaderId());
    }

    @Test
    void anyCandidatesListsEveryLeaderOfTheSelectedSourcesWithBlockedReasons() {
        TrainingCourse source = course("A", prerequisiteType, OffsetDateTime.now());
        Leader free = leader("Hai", parish);
        Leader already = leader("Ba", parish);
        stubSourceWith(source, participation(free, source, CourseFinalResult.PASSED), participation(already, source, CourseFinalResult.FAILED));
        when(courseRepository.findByCodeWithType("TARGET2")).thenReturn(Optional.of(copyTarget(false)));
        when(participationRepository.findLeaderIdsByCourseCode("TARGET2")).thenReturn(Set.of(already.getId()));

        List<Candidate> candidates = service.anyCandidates("TARGET2", null, List.of("A"), mock(Authentication.class));

        assertEquals(2, candidates.size());
        assertNull(candidates.stream().filter(item -> item.leaderId().equals(free.getId())).findFirst().orElseThrow().blockedReason());
        assertEquals(CourseCopyService.BLOCK_EXISTING, candidates.stream().filter(item -> item.leaderId().equals(already.getId())).findFirst().orElseThrow().blockedReason());
    }

    @Test
    void formSourcesAreOnlyFinishedCoursesOfThePrerequisiteTypeInTheSameDiocese() {
        TrainingCourse own = course("A", prerequisiteType, OffsetDateTime.now());
        TrainingCourse foreign = course("B", prerequisiteType, OffsetDateTime.now());
        foreign.setDiocese(diocese());
        when(courseRepository.findFinishedByCourseTypeCode("SMTL_I")).thenReturn(List.of(own, foreign));
        when(courseRepository.findByCodeWithType("TARGET2")).thenReturn(Optional.of(copyTarget(false)));

        List<phucuong.ht.modules.training.courseCopy.model.CourseCopyModels.AnySource> sources = service.anySources("TARGET2", null, mock(Authentication.class));

        assertEquals(List.of("A"), sources.stream().map(item -> item.courseCode()).toList());
    }

    @Test
    void courseTypeWithoutPrerequisiteHasNoSourcesAndRejectsAnySelectedCourse() {
        TrainingCourse rootTarget = course("TARGET2", type("ROOT", null), null);
        when(courseRepository.findByCodeWithType("TARGET2")).thenReturn(Optional.of(rootTarget));
        when(courseRepository.findFinishedByCourseTypeCode(anyString())).thenReturn(List.of(course("A", prerequisiteType, OffsetDateTime.now())));

        assertTrue(service.anySources("TARGET2", null, mock(Authentication.class)).isEmpty());
        assertThrows(ApiException.class, () -> service.anyCandidates("TARGET2", null, List.of("A"), mock(Authentication.class)));
    }

    private TrainingCourse course(String code, TrainingCourseType type, OffsetDateTime finishedAt) {
        TrainingCourse course = new TrainingCourse();
        course.setCourseCode(code);
        course.setCourseName("Khóa " + code);
        course.setCourseType(type);
        course.setDiocese(diocese);
        course.setFinishedAt(finishedAt);
        course.setStatus(Boolean.TRUE);
        return course;
    }

    private static TrainingCourseType type(String code, TrainingCourseType prerequisite) {
        TrainingCourseType type = new TrainingCourseType();
        type.setTypeCode(code);
        type.setTypeName("Loại " + code);
        type.setManagementUnitType(UnitType.DIOCESE);
        type.setPrerequisiteType(prerequisite);
        return type;
    }

    private static CourseParticipation participation(Leader leader, TrainingCourse course, CourseFinalResult result) {
        CourseParticipation participation = new CourseParticipation();
        participation.setId(UUID.randomUUID().toString());
        participation.setLeader(leader);
        participation.setCourse(course);
        participation.setFinalResult(result);
        participation.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        return participation;
    }

    private static Leader leader(String name, Parish parish) {
        Leader leader = new Leader();
        leader.setId(UUID.randomUUID());
        leader.setFirstName(name);
        leader.setLastName("Nguyễn");
        leader.setParish(parish);
        leader.setStatus(Boolean.TRUE);
        return leader;
    }

    private static Diocese diocese() {
        Diocese diocese = new Diocese();
        diocese.setId(UUID.randomUUID());
        diocese.setName("Phú Cường");
        return diocese;
    }

    private static Deanery deanery(String name, Diocese diocese) {
        Deanery deanery = new Deanery();
        deanery.setId(UUID.randomUUID());
        deanery.setName(name);
        deanery.setDiocese(diocese);
        return deanery;
    }

    private static Parish parish(String name, Deanery deanery) {
        Parish parish = new Parish();
        parish.setId(UUID.randomUUID());
        parish.setName(name);
        parish.setDeanery(deanery);
        return parish;
    }
}
