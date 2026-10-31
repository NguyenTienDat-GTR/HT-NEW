package phucuong.ht.modules.training.courseAttendance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.TrainingTestFixtures;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.InvitationFilter;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.ParticipantQuery;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.ParticipantRow;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.ScanResult;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.ScanStatus;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.Summary;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipantSupport;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseAttendanceServiceTest {

    private static final String SECRET = "unit-test-attendance-secret-0123456789";

    private CourseParticipantSupport participantSupport;
    private CourseParticipationRepository repository;
    private AttendanceTokenService tokens;
    private CourseAttendanceService service;
    private final phucuong.ht.common.utils.AuditActorNameResolver resolver = mock(phucuong.ht.common.utils.AuditActorNameResolver.class);

    private Parish parish;
    private TrainingCourse course;
    private CourseParticipation ann;
    private final Authentication scanner = new UsernamePasswordAuthenticationToken("scanner1", null, List.of());

    @BeforeEach
    void setUp() {
        participantSupport = mock(CourseParticipantSupport.class);
        repository = mock(CourseParticipationRepository.class);
        tokens = new AttendanceTokenService(SECRET);
        CommonValidationHelper validationHelper = new CommonValidationHelper(new securityUtils());
        service = new CourseAttendanceService(participantSupport, repository, tokens, new CourseActivityGuard(validationHelper), validationHelper, resolver);

        Diocese diocese = TrainingTestFixtures.diocese("Giáo phận Phú Cường");
        Deanery deanery = TrainingTestFixtures.deanery("Giáo hạt A", diocese);
        parish = TrainingTestFixtures.parish("Giáo xứ B", deanery);
        course = TrainingTestFixtures.course("KH-1", "Khóa 1", deanery);
        course.setAttendanceEnabled(true);
        course.setStartDate(LocalDate.now().minusDays(1));
        course.setEndDate(LocalDate.now().plusDays(3));
        when(participantSupport.requireRegularCourse(eq("KH-1"), any())).thenReturn(course);

        ann = participation("p-ann", "An", "an@x.vn", course);
        when(repository.findByIdWithLeaderAndCourse("p-ann")).thenReturn(Optional.of(ann));
    }

    private CourseParticipation participation(String id, String firstName, String email, TrainingCourse inCourse) {
        return TrainingTestFixtures.participation(id, TrainingTestFixtures.leader("Giuse", "Nguyễn", firstName, email, parish), inCourse, "Đội 1");
    }

    private ApiException failure(Runnable action) {
        return assertThrows(ApiException.class, action::run);
    }

    // ---------- quét QR ----------

    @Test
    void lookupReturnsReadyWithoutWritingAnything() {
        ScanResult result = service.lookup("KH-1", tokens.tokenFor("KH-1", "p-ann"));
        assertEquals(ScanStatus.READY, result.status());
        assertEquals("Nguyễn An", result.fullName());
        assertNull(ann.getCheckedInAt());
        verify(repository, never()).save(any());
    }

    @Test
    void lookupReportsAlreadyCheckedInWithTheFirstTime() {
        OffsetDateTime first = OffsetDateTime.now().minusMinutes(5);
        ann.setCheckedInAt(first);
        ScanResult result = service.lookup("KH-1", tokens.tokenFor("KH-1", "p-ann"));
        assertEquals(ScanStatus.ALREADY_CHECKED_IN, result.status());
        assertEquals(first, result.checkedInAt());
        verify(repository, never()).save(any());
    }

    @Test
    void lookupAppliesTheSameGatesAsScan() {
        assertEquals(400, failure(() -> service.lookup("KH-1", "HTATT2.KH-1.p-ann.AAAAAAAAAAAAAAAAAAAAAA")).getStatus().value());
        ann.setApprovalStatus(WorkflowApprovalStatus.REJECTED);
        assertEquals(409, failure(() -> service.lookup("KH-1", tokens.tokenFor("KH-1", "p-ann"))).getStatus().value());
        ann.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
        course.setAttendanceEnabled(false);
        assertEquals(409, failure(() -> service.lookup("KH-1", tokens.tokenFor("KH-1", "p-ann"))).getStatus().value());
    }

    @Test
    void firstScanChecksTheParticipantIn() {
        ScanResult result = service.scan("KH-1", tokens.tokenFor("KH-1", "p-ann"), scanner);

        assertEquals(ScanStatus.CHECKED_IN, result.status());
        assertEquals("p-ann", result.participationId());
        assertEquals("Giuse", result.holyName());
        assertEquals("Nguyễn An", result.fullName());
        assertEquals("Giáo xứ B", result.parishName());
        assertEquals("Giáo hạt A", result.deaneryName());
        assertEquals("Đội 1", result.teamName());
        assertNotNull(result.checkedInAt());
        assertNotNull(ann.getCheckedInAt());
        assertEquals("scanner1", ann.getCheckedInBy());
        verify(repository).save(ann);
    }

    @Test
    void secondScanKeepsTheFirstTimeAndDoesNotWrite() {
        OffsetDateTime first = OffsetDateTime.now().minusMinutes(30);
        ann.setCheckedInAt(first);
        ann.setCheckedInBy("someone");

        ScanResult result = service.scan("KH-1", tokens.tokenFor("KH-1", "p-ann"), scanner);

        assertEquals(ScanStatus.ALREADY_CHECKED_IN, result.status());
        assertEquals(first, result.checkedInAt());
        assertEquals("someone", ann.getCheckedInBy());
        verify(repository, never()).save(any());
    }

    @Test
    void forgedOrMalformedTokensAreRejectedBeforeAnyLookup() {
        for (String bad : new String[]{"HTATT2.KH-1.p-ann.AAAAAAAAAAAAAAAAAAAAAA", "p-ann", "https://x/certificates/CODE", "   ", "HTATT2.p-ann"}) {
            ApiException error = failure(() -> service.scan("KH-1", bad, scanner));
            assertEquals(400, error.getStatus().value(), bad);
            assertTrue(error.getMessage().contains("Mã QR không hợp lệ"), bad);
        }
        failure(() -> service.scan("KH-1", null, scanner));
        verify(repository, never()).findByIdWithLeaderAndCourse(any());
        verify(repository, never()).save(any());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String foreign = new AttendanceTokenService("another-secret-0123456789-abcdef").tokenFor("KH-1", "p-ann");
        assertEquals(400, failure(() -> service.scan("KH-1", foreign, scanner)).getStatus().value());
        assertNull(ann.getCheckedInAt());
    }

    @Test
    void validTokenOfAnUnknownParticipationIsNotFound() {
        ApiException error = failure(() -> service.scan("KH-1", tokens.tokenFor("KH-1", "ghost"), scanner));
        assertEquals(404, error.getStatus().value());
    }

    @Test
    void tokenOfAnotherCourseIsRejected() {
        TrainingCourse other = TrainingTestFixtures.course("KH-2", "Khóa 2", null);
        CourseParticipation foreign = participation("p-foreign", "Bình", "b@x.vn", other);
        when(repository.findByIdWithLeaderAndCourse("p-foreign")).thenReturn(Optional.of(foreign));

        ApiException error = failure(() -> service.scan("KH-1", tokens.tokenFor("KH-1", "p-foreign"), scanner));

        assertEquals(400, error.getStatus().value());
        assertTrue(error.getMessage().contains("khóa huấn luyện khác"));
        assertNull(foreign.getCheckedInAt());
    }

    @Test
    void tokenOfAnotherCourseThanTheScanPageIsRejected() {
        assertEquals(400, failure(() -> service.scan("KH-1", tokens.tokenFor("KH-2", "p-ann"), scanner)).getStatus().value());
        assertNull(ann.getCheckedInAt());
    }

    @Test
    void scanWithoutChoosingACourseTakesTheCourseFromTheToken() {
        String token = tokens.tokenFor("KH-1", "p-ann");
        assertEquals("KH-1", service.courseCodeOf(token));
        assertEquals(ScanStatus.READY, service.lookup(token).status());
        assertEquals(ScanStatus.CHECKED_IN, service.scan(token, scanner).status());
        assertEquals(ScanStatus.ALREADY_CHECKED_IN, service.scan(token, scanner).status());
    }

    @Test
    void headerScanReportsTheCourseState() {
        String token = tokens.tokenFor("KH-1", "p-ann");
        course.setAttendanceEnabled(false);
        assertTrue(failure(() -> service.lookup(token)).getMessage().contains("chưa bật điểm danh"));
        course.setAttendanceEnabled(true);
        course.setStartDate(LocalDate.now().minusDays(5));
        course.setEndDate(LocalDate.now().minusDays(1));
        assertTrue(failure(() -> service.lookup(token)).getMessage().contains("đã kết thúc"));
        course.setEndDate(LocalDate.now().plusDays(1));
        course.setStatus(Boolean.FALSE);
        assertTrue(failure(() -> service.lookup(token)).getMessage().contains("vô hiệu hóa"));
    }

    @Test
    void legacyTokenAsksForANewOne() {
        ApiException error = failure(() -> service.lookup("HTATT1.p-ann.AAAAAAAAAAAAAAAAAAAAAA"));
        assertEquals(400, error.getStatus().value());
        assertTrue(error.getMessage().contains("phiên bản cũ"));
        assertEquals(400, failure(() -> service.courseCodeOf("HTATT1.p-ann.AAAAAAAAAAAAAAAAAAAAAA")).getStatus().value());
    }

    @Test
    void participantRemovedFromTheListCannotCheckIn() {
        ann.setApprovalStatus(WorkflowApprovalStatus.REJECTED);
        ann.setParticipationStatus(ParticipationStatus.REJECTED);
        ApiException error = failure(() -> service.scan("KH-1", tokens.tokenFor("KH-1", "p-ann"), scanner));
        assertEquals(409, error.getStatus().value());
        assertNull(ann.getCheckedInAt());
    }

    @Test
    void pendingParticipantCannotCheckIn() {
        ann.setApprovalStatus(WorkflowApprovalStatus.PENDING);
        assertEquals(409, failure(() -> service.scan("KH-1", tokens.tokenFor("KH-1", "p-ann"), scanner)).getStatus().value());
    }

    @Test
    void disabledParticipantRecordCannotCheckIn() {
        ann.setStatus(Boolean.FALSE);
        assertEquals(409, failure(() -> service.scan("KH-1", tokens.tokenFor("KH-1", "p-ann"), scanner)).getStatus().value());
    }

    @Test
    void scanIsRefusedWhenAttendanceIsNotEnabled() {
        course.setAttendanceEnabled(false);
        ApiException error = failure(() -> service.scan("KH-1", tokens.tokenFor("KH-1", "p-ann"), scanner));
        assertEquals(409, error.getStatus().value());
        assertTrue(error.getMessage().contains("chưa bật điểm danh"));
        verify(repository, never()).save(any());
    }

    @Test
    void scanIsRefusedWhenAttendanceFlagIsNull() {
        course.setAttendanceEnabled(null);
        assertEquals(409, failure(() -> service.scan("KH-1", tokens.tokenFor("KH-1", "p-ann"), scanner)).getStatus().value());
    }

    @Test
    void scanIsRefusedOnDisabledEndedOrFinishedCourses() {
        course.setStatus(Boolean.FALSE);
        assertEquals(409, failure(() -> service.scan("KH-1", tokens.tokenFor("KH-1", "p-ann"), scanner)).getStatus().value());

        course.setStatus(Boolean.TRUE);
        course.setEndDate(LocalDate.now().minusDays(1));
        course.setStartDate(LocalDate.now().minusDays(5));
        ApiException ended = failure(() -> service.scan("KH-1", tokens.tokenFor("KH-1", "p-ann"), scanner));
        assertEquals(409, ended.getStatus().value());
        assertTrue(ended.getMessage().contains("đã kết thúc"));

        course.setEndDate(LocalDate.now().plusDays(3));
        course.setFinishedAt(OffsetDateTime.now());
        assertEquals(409, failure(() -> service.scan("KH-1", tokens.tokenFor("KH-1", "p-ann"), scanner)).getStatus().value());
        assertNull(ann.getCheckedInAt());
    }

    @Test
    void scanIsRefusedBeforeTheCourseStartsWithTheStartDate() {
        course.setStartDate(LocalDate.now().plusDays(2));
        course.setEndDate(LocalDate.now().plusDays(4));
        String token = tokens.tokenFor("KH-1", "p-ann");
        ApiException early = failure(() -> service.lookup(token));
        assertEquals(409, early.getStatus().value());
        assertTrue(early.getMessage().contains("chưa diễn ra"));
        assertTrue(early.getMessage().contains(LocalDate.now().plusDays(2).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        assertEquals(409, failure(() -> service.scan(token, scanner)).getStatus().value());
        assertEquals(409, failure(() -> service.setCheckedIn("KH-1", "p-ann", true, scanner)).getStatus().value());
        assertNull(ann.getCheckedInAt());
    }

    @Test
    void scanIsRefusedWhileScoreEditingIsLocked() {
        course.setScoreLocked(Boolean.TRUE);
        String token = tokens.tokenFor("KH-1", "p-ann");
        assertTrue(failure(() -> service.lookup(token)).getMessage().contains("khóa chỉnh sửa điểm"));
        assertEquals(409, failure(() -> service.scan("KH-1", token, scanner)).getStatus().value());
        assertEquals(409, failure(() -> service.setCheckedIn("KH-1", "p-ann", true, scanner)).getStatus().value());
        assertNull(ann.getCheckedInAt());
        course.setScoreLocked(Boolean.FALSE);
        assertEquals(ScanStatus.CHECKED_IN, service.scan("KH-1", token, scanner).status());
    }

    @Test
    void scanWorksOnTheLastDayOfTheCourse() {
        course.setStartDate(LocalDate.now().minusDays(3));
        course.setEndDate(LocalDate.now());
        assertEquals(ScanStatus.CHECKED_IN, service.scan("KH-1", tokens.tokenFor("KH-1", "p-ann"), scanner).status());
    }

    @Test
    void pastCoursesDoNotSupportAttendance() {
        when(participantSupport.requireRegularCourse(eq("PAST"), any()))
                .thenThrow(new CommonValidationHelper(new securityUtils()).validationError("Khóa quá khứ không áp dụng điểm danh"));
        assertEquals(400, failure(() -> service.scan("PAST", "x", scanner)).getStatus().value());
        assertEquals(400, failure(() -> service.summary("PAST")).getStatus().value());
    }

    @Test
    void scanWithoutIdentifiableScannerIsForbidden() {
        Authentication anonymous = new UsernamePasswordAuthenticationToken(" ", null, List.of());
        assertEquals(403, failure(() -> service.scan("KH-1", tokens.tokenFor("KH-1", "p-ann"), anonymous)).getStatus().value());
        assertNull(ann.getCheckedInAt());
    }

    // ---------- điểm danh thủ công ----------

    @Test
    void manualCheckInAndUndo() {
        ScanResult checkedIn = service.setCheckedIn("KH-1", "p-ann", true, scanner);
        assertEquals(ScanStatus.CHECKED_IN, checkedIn.status());
        assertNotNull(ann.getCheckedInAt());

        ScanResult again = service.setCheckedIn("KH-1", "p-ann", true, scanner);
        assertEquals(ScanStatus.ALREADY_CHECKED_IN, again.status());
        verify(repository, times(1)).save(ann);

        ScanResult undone = service.setCheckedIn("KH-1", "p-ann", false, scanner);
        assertEquals(ScanStatus.CHECK_IN_CANCELLED, undone.status());
        assertNull(ann.getCheckedInAt());
        assertNull(ann.getCheckedInBy());
        assertNull(undone.checkedInAt());
    }

    @Test
    void undoingACheckInThatNeverHappenedIsHarmless() {
        ScanResult result = service.setCheckedIn("KH-1", "p-ann", false, scanner);
        assertEquals(ScanStatus.CHECK_IN_CANCELLED, result.status());
        assertNull(ann.getCheckedInAt());
    }

    @Test
    void manualCheckInRejectsOtherCoursesAndUnknownParticipations() {
        TrainingCourse other = TrainingTestFixtures.course("KH-2", "Khóa 2", null);
        when(repository.findByIdWithLeaderAndCourse("p-other")).thenReturn(Optional.of(participation("p-other", "C", "c@x.vn", other)));
        assertEquals(400, failure(() -> service.setCheckedIn("KH-1", "p-other", true, scanner)).getStatus().value());
        assertEquals(404, failure(() -> service.setCheckedIn("KH-1", "nope", true, scanner)).getStatus().value());
    }

    @Test
    void manualCheckInRespectsTheSameGatesAsScanning() {
        course.setAttendanceEnabled(false);
        assertEquals(409, failure(() -> service.setCheckedIn("KH-1", "p-ann", true, scanner)).getStatus().value());
        course.setAttendanceEnabled(true);
        ann.setApprovalStatus(WorkflowApprovalStatus.REJECTED);
        assertEquals(409, failure(() -> service.setCheckedIn("KH-1", "p-ann", true, scanner)).getStatus().value());
    }

    // ---------- tổng quan ----------

    @Test
    void summaryCountsCheckInsInvitationsAndMissingEmails() {
        CourseParticipation bich = participation("p-bich", "Bích", "bich@x.vn", course);
        bich.setCheckedInAt(OffsetDateTime.now());
        bich.setInvitationSentAt(OffsetDateTime.now());
        CourseParticipation chi = participation("p-chi", "Chi", "khong-hop-le", course);
        chi.setInvitationError("Email không hợp lệ");
        CourseParticipation dung = participation("p-dung", "Dũng", "  ", course);
        when(participantSupport.participantsOf("KH-1")).thenReturn(List.of(ann, bich, chi, dung));
        course.setAutoSendInvitation(true);

        Summary summary = service.summary("KH-1");

        assertTrue(summary.attendanceEnabled());
        assertTrue(summary.autoSendInvitation());
        assertEquals(4, summary.totalParticipants());
        assertEquals(1, summary.checkedIn());
        assertEquals(3, summary.notCheckedIn());
        assertEquals(1, summary.invitationSent());
        assertEquals(3, summary.invitationPending());
        assertEquals(1, summary.invitationFailed());
        assertEquals(2, summary.withoutEmail());
    }

    // ---------- chi tiết hồ sơ ----------

    @Test
    void detailCarriesLeaderEmailAttendanceAndActorNames() {
        ann.setCheckedInAt(OffsetDateTime.now());
        ann.setCheckedInBy("scanner1");
        ann.setCreatedBy("creator");
        ann.setApprovedBy("approver");
        ann.setUpdatedBy("scanner1");
        ann.setInvitationError("Không gửi được email: 550");
        when(resolver.resolve("scanner1")).thenReturn("Người quét");
        when(resolver.resolve("creator")).thenReturn("Người tạo");
        when(resolver.resolve("approver")).thenReturn("Người duyệt");

        var detail = service.participantDetail("KH-1", "p-ann");

        assertEquals("Nguyễn An", detail.fullName());
        assertEquals("Giuse", detail.holyName());
        assertEquals("Giáo xứ B", detail.parishName());
        assertEquals("Giáo hạt A", detail.deaneryName());
        assertEquals("Giáo phận Phú Cường", detail.dioceseName());
        assertEquals("an@x.vn", detail.email());
        assertNull(detail.emailProblem());
        assertEquals("Không gửi được email: 550", detail.invitationError());
        assertEquals("scanner1", detail.checkedInBy());
        assertEquals("Người quét", detail.checkedInByName());
        assertEquals("Người tạo", detail.createdByName());
        assertEquals("Người duyệt", detail.approvedByName());
        assertEquals("APPROVED", detail.approvalStatus());
        assertTrue(detail.attendanceEnabled());
    }

    @Test
    void detailToleratesMissingActorsAndFlagsABadEmail() {
        ann.getLeader().setEmail("sai");
        var detail = service.participantDetail("KH-1", "p-ann");
        assertNull(detail.checkedInByName());
        assertNull(detail.approvedByName());
        assertTrue(detail.emailProblem().startsWith("Email không hợp lệ"));
        verify(resolver, never()).resolve(any());
    }

    @Test
    void detailRejectsUnknownOtherCourseAndRemovedParticipants() {
        assertEquals(404, failure(() -> service.participantDetail("KH-1", "nope")).getStatus().value());
        TrainingCourse other = TrainingTestFixtures.course("KH-2", "Khóa 2", null);
        when(repository.findByIdWithLeaderAndCourse("p-other")).thenReturn(Optional.of(participation("p-other", "C", "c@x.vn", other)));
        assertEquals(404, failure(() -> service.participantDetail("KH-1", "p-other")).getStatus().value());
        ann.setApprovalStatus(WorkflowApprovalStatus.REJECTED);
        assertEquals(409, failure(() -> service.participantDetail("KH-1", "p-ann")).getStatus().value());
    }

    // ---------- danh sách điểm danh / email ----------

    private List<CourseParticipation> givenListedParticipants() {
        CourseParticipation bich = participation("p-bich", "Bích", "bich@x.vn", course);
        bich.setCheckedInAt(OffsetDateTime.now());
        bich.setCheckedInBy("scanner1");
        bich.setInvitationSentAt(OffsetDateTime.now());
        CourseParticipation chi = participation("p-chi", "Chi", "khong-hop-le", course);
        chi.setInvitationError("Email không hợp lệ: khong-hop-le");
        chi.setTeamName("Đội 2");
        CourseParticipation dung = participation("p-dung", "Dũng", " ", course);
        dung.setTeamName(null);
        List<CourseParticipation> all = List.of(dung, chi, bich, ann);
        when(participantSupport.participantsOf("KH-1")).thenReturn(all);
        return all;
    }

    private Page<ParticipantRow> list(String search, String team, Boolean checkedIn, InvitationFilter invitation, int page, int size) {
        return service.participants("KH-1", new ParticipantQuery(search, team, checkedIn, invitation, page, size));
    }

    @Test
    void listIsOrderedByTeamThenNameWithUnassignedLast() {
        givenListedParticipants();
        List<String> ids = list(null, null, null, null, 0, 20).getContent().stream().map(ParticipantRow::id).toList();
        // Đội 1: An, Bích; Đội 2: Chi; chưa có đội: Dũng.
        assertEquals(List.of("p-ann", "p-bich", "p-chi", "p-dung"), ids);
    }

    @Test
    void listRowsCarryEmailAndAttendanceState() {
        givenListedParticipants();
        List<ParticipantRow> rows = list(null, null, null, null, 0, 20).getContent();
        ParticipantRow bich = rows.stream().filter(r -> r.id().equals("p-bich")).findFirst().orElseThrow();
        assertEquals("Giuse", bich.holyName());
        assertEquals("Nguyễn Bích", bich.fullName());
        assertEquals("Giáo xứ B", bich.parishName());
        assertEquals("Giáo hạt A", bich.deaneryName());
        assertEquals("bich@x.vn", bich.email());
        assertNull(bich.emailProblem());
        assertNotNull(bich.invitationSentAt());
        assertEquals("scanner1", bich.checkedInBy());
        ParticipantRow chi = rows.stream().filter(r -> r.id().equals("p-chi")).findFirst().orElseThrow();
        assertTrue(chi.emailProblem().startsWith("Email không hợp lệ"));
        assertEquals("Email không hợp lệ: khong-hop-le", chi.invitationError());
        ParticipantRow dung = rows.stream().filter(r -> r.id().equals("p-dung")).findFirst().orElseThrow();
        assertEquals("Hồ sơ chưa có email", dung.emailProblem());
    }

    @Test
    void listFiltersByCheckInState() {
        givenListedParticipants();
        assertEquals(List.of("p-bich"), list(null, null, true, null, 0, 20).getContent().stream().map(ParticipantRow::id).toList());
        assertEquals(List.of("p-ann", "p-chi", "p-dung"), list(null, null, false, null, 0, 20).getContent().stream().map(ParticipantRow::id).toList());
    }

    @Test
    void listFiltersByInvitationState() {
        givenListedParticipants();
        assertEquals(List.of("p-bich"), ids(list(null, null, null, InvitationFilter.SENT, 0, 20)));
        assertEquals(List.of("p-ann", "p-chi", "p-dung"), ids(list(null, null, null, InvitationFilter.PENDING, 0, 20)));
        assertEquals(List.of("p-chi"), ids(list(null, null, null, InvitationFilter.FAILED, 0, 20)));
        assertEquals(List.of("p-chi", "p-dung"), ids(list(null, null, null, InvitationFilter.NO_EMAIL, 0, 20)));
    }

    @Test
    void listFiltersByTeamIgnoringCaseAndAccents() {
        givenListedParticipants();
        assertEquals(List.of("p-chi"), ids(list(null, "doi 2", null, null, 0, 20)));
        assertEquals(List.of("p-ann", "p-bich"), ids(list(null, " ĐỘI 1 ", null, null, 0, 20)));
        assertEquals(4, list(null, "  ", null, null, 0, 20).getTotalElements());
        assertTrue(list(null, "Đội 9", null, null, 0, 20).isEmpty());
    }

    @Test
    void listSearchIgnoresCaseAndAccentsAcrossNameParishAndEmail() {
        givenListedParticipants();
        assertEquals(List.of("p-bich"), ids(list("bich", null, null, null, 0, 20)));
        assertEquals(List.of("p-bich"), ids(list("BÍCH", null, null, null, 0, 20)));
        assertEquals(List.of("p-dung"), ids(list("dung", null, null, null, 0, 20)));
        assertEquals(4, list("giao xu b", null, null, null, 0, 20).getTotalElements());
        assertEquals(List.of("p-chi"), ids(list("khong-hop-le", null, null, null, 0, 20)));
        assertEquals(List.of("p-chi"), ids(list("doi 2", null, null, null, 0, 20)));
        assertTrue(list("khong ton tai", null, null, null, 0, 20).isEmpty());
    }

    @Test
    void listFiltersCombineWithAnd() {
        givenListedParticipants();
        assertEquals(List.of("p-ann"), ids(list(null, "Đội 1", false, InvitationFilter.PENDING, 0, 20)));
        assertTrue(list("an", null, true, null, 0, 20).isEmpty());
    }

    @Test
    void listPaginatesAndClampsBadPagingValues() {
        givenListedParticipants();
        Page<ParticipantRow> first = list(null, null, null, null, 0, 3);
        assertEquals(4, first.getTotalElements());
        assertEquals(2, first.getTotalPages());
        assertEquals(3, first.getContent().size());
        assertEquals(List.of("p-dung"), ids(list(null, null, null, null, 1, 3)));
        assertTrue(list(null, null, null, null, 9, 3).getContent().isEmpty());
        assertEquals(4, list(null, null, null, null, -5, 0).getTotalElements());
        assertEquals(1, list(null, null, null, null, 0, 0).getContent().size());
        assertEquals(4, list(null, null, null, null, 0, 1000).getContent().size());
    }

    @Test
    void listOfAnEmptyCourseIsEmpty() {
        when(participantSupport.participantsOf("KH-1")).thenReturn(List.of());
        assertTrue(list(null, null, null, null, 0, 20).isEmpty());
    }

    private static List<String> ids(Page<ParticipantRow> page) {
        return page.getContent().stream().map(ParticipantRow::id).toList();
    }

    @Test
    void summaryOfAnEmptyCourseIsAllZeros() {
        when(participantSupport.participantsOf("KH-1")).thenReturn(List.of());
        course.setAttendanceEnabled(false);
        Summary summary = service.summary("KH-1");
        assertFalse(summary.attendanceEnabled());
        assertEquals(0, summary.totalParticipants());
        assertEquals(0, summary.notCheckedIn());
        assertEquals(0, summary.invitationPending());
    }
}
