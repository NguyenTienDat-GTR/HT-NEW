package phucuong.ht.modules.training.courseInvitation.service;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.auth.exception.ApiException;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.TrainingTestFixtures;
import phucuong.ht.modules.training.courseAttendance.service.AttendanceTokenService;
import phucuong.ht.modules.training.courseInvitation.model.InvitationModels.Issue;
import phucuong.ht.modules.training.courseInvitation.model.InvitationModels.Result;
import phucuong.ht.modules.training.courseInvitation.model.InvitationModels.SendRequest;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipantSupport;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseInvitationServiceTest {

    private JavaMailSender mailSender;
    private CourseParticipantSupport participantSupport;
    private CourseParticipationRepository repository;
    private CourseInvitationService service;

    private Parish parish;
    private TrainingCourse course;
    private List<CourseParticipation> participants;
    private final List<MimeMessage> sent = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        participantSupport = mock(CourseParticipantSupport.class);
        repository = mock(CourseParticipationRepository.class);
        CommonValidationHelper validationHelper = new CommonValidationHelper(new securityUtils());
        service = new CourseInvitationService(mailSender, mock(PlatformTransactionManager.class), participantSupport, repository,
                new AttendanceTokenService("unit-test-attendance-secret-0123456789"), new CourseActivityGuard(validationHelper), validationHelper);
        ReflectionTestUtils.setField(service, "from", "no-reply@ht.vn");

        Diocese diocese = TrainingTestFixtures.diocese("Giáo phận Phú Cường");
        Deanery deanery = TrainingTestFixtures.deanery("Giáo hạt Thủ Dầu Một", diocese);
        parish = TrainingTestFixtures.parish("Giáo xứ Chánh Tòa", deanery);
        course = TrainingTestFixtures.course("KH-1", "Khóa Dự Trưởng", deanery);
        course.setAttendanceEnabled(true);

        when(participantSupport.requireRegularCourse(eq("KH-1"), any())).thenReturn(course);
        participants = new ArrayList<>(List.of(
                participation("p-1", "An", "an@mail.vn"),
                participation("p-2", "Bình", "binh@mail.vn"),
                participation("p-3", "Chi", "chi@mail.vn")));
        when(participantSupport.participantsOf("KH-1")).thenAnswer(invocation -> participants);
        when(repository.findAllByIdsWithLeaderAndCourse(anyList())).thenAnswer(invocation -> {
            List<String> ids = invocation.getArgument(0);
            return participants.stream().filter(p -> ids.contains(p.getId())).toList();
        });
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(invocation -> {
            MimeMessage captured = invocation.getArgument(0);
            captured.saveChanges();
            sent.add(captured);
            return null;
        }).when(mailSender).send(any(MimeMessage.class));
    }

    private CourseParticipation participation(String id, String firstName, String email) {
        return TrainingTestFixtures.participation(id, TrainingTestFixtures.leader("Giuse", "Nguyễn", firstName, email, parish), course, "Đội 1");
    }

    private CourseParticipation byId(String id) {
        return participants.stream().filter(p -> p.getId().equals(id)).findFirst().orElseThrow();
    }

    private static String recipient(MimeMessage message) throws Exception {
        Address[] to = message.getAllRecipients();
        return to == null || to.length == 0 ? null : to[0].toString();
    }

    private List<String> recipients() throws Exception {
        List<String> list = new ArrayList<>();
        for (MimeMessage message : sent) {
            list.add(recipient(message));
        }
        return list;
    }

    // Gom mọi phần của thư (đệ quy) dưới dạng "content-type|disposition|content-id".
    private static List<String> parts(Part part) throws Exception {
        List<String> found = new ArrayList<>();
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart body = multipart.getBodyPart(i);
                found.addAll(parts(body));
            }
        } else {
            String[] contentId = part.getHeader("Content-ID");
            String type = part.getContentType().split(";")[0].trim().toLowerCase();
            found.add(type + "|" + part.getDisposition() + "|" + (contentId == null ? "" : contentId[0]));
        }
        return found;
    }

    private static String htmlOf(Part part) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String html = htmlOf(multipart.getBodyPart(i));
                if (html != null) {
                    return html;
                }
            }
            return null;
        }
        return part.isMimeType("text/html") ? (String) part.getContent() : null;
    }

    private Result sendAll(boolean includeQr, boolean resend) {
        return service.sendInvitations("KH-1", null, includeQr, resend);
    }

    // ---------- kiểm tra email ----------

    @Test
    void emailProblemAcceptsNormalAddresses() {
        for (String ok : new String[]{"a@b.vn", "nguyen.van-a+tag@mail.example.com", "x_y@sub.domain.co.uk", "O'Neil@mail.vn", " a@b.vn "}) {
            assertNull(CourseInvitationService.emailProblem(ok), ok);
        }
    }

    @Test
    void emailProblemFlagsMissingAndMalformedAddresses() {
        assertEquals("Hồ sơ chưa có email", CourseInvitationService.emailProblem(null));
        assertEquals("Hồ sơ chưa có email", CourseInvitationService.emailProblem(""));
        assertEquals("Hồ sơ chưa có email", CourseInvitationService.emailProblem("   "));
        for (String bad : new String[]{"abc", "a@b", "a b@c.vn", "a@@b.vn", "a@b..vn", "@b.vn", "a@.vn", "a@b.", "a@-b.vn",
                "a@b.vn\r\nBcc: x@y.z", "a@b.vn,c@d.vn", "<a@b.vn>", "tên@mail.vn"}) {
            String problem = CourseInvitationService.emailProblem(bad);
            assertNotNull(problem, bad);
            assertTrue(problem.startsWith("Email không hợp lệ"), bad);
        }
        assertNotNull(CourseInvitationService.emailProblem("a".repeat(250) + "@b.vn"));
    }

    @Test
    void overlongBadEmailIsShortenedInTheMessage() {
        String problem = CourseInvitationService.emailProblem("x".repeat(300));
        assertTrue(problem.length() < 130, problem);
    }

    @Test
    void hasValidEmailIsFalseForNullLeader() {
        assertFalse(CourseInvitationService.hasValidEmail(null));
        assertTrue(CourseInvitationService.hasValidEmail(byId("p-1").getLeader()));
    }

    // ---------- gửi thành công ----------

    @Test
    void sendsToEveryParticipantAndRecordsTheTime() throws Exception {
        Result result = sendAll(false, false);

        assertEquals(3, result.total());
        assertEquals(3, result.sent());
        assertEquals(0, result.failed());
        assertEquals(0, result.skippedAlreadySent());
        assertTrue(result.issues().isEmpty());
        assertEquals(List.of("an@mail.vn", "binh@mail.vn", "chi@mail.vn"), recipients());
        for (CourseParticipation participation : participants) {
            assertNotNull(participation.getInvitationSentAt());
            assertNull(participation.getInvitationError());
        }
        verify(repository, org.mockito.Mockito.times(3)).save(any());
    }

    @Test
    void mailCarriesFromSubjectAndTheRequiredInformation() throws Exception {
        sendAll(false, false);
        MimeMessage message = sent.get(0);

        assertEquals("no-reply@ht.vn", message.getFrom()[0].toString());
        assertEquals("Thông tin tham dự khóa huấn luyện: Khóa Dự Trưởng", message.getSubject());
        String html = htmlOf(message);
        for (String expected : new String[]{"Giuse", "Nguyễn An", "Giáo xứ Chánh Tòa", "Giáo hạt Thủ Dầu Một", "Giáo phận Phú Cường",
                "Khóa Dự Trưởng", "từ ", "Đội 1"}) {
            assertTrue(html.contains(expected), "thiếu " + expected + " trong " + html);
        }
    }

    @Test
    void blankFromIsNotSetOnTheMessage() throws Exception {
        ReflectionTestUtils.setField(service, "from", " ");
        sendAll(false, false);
        assertNull(sent.get(0).getFrom());
    }

    @Test
    void qrIsInlinedAndAttachedOnlyWhenRequestedAndAttendanceIsEnabled() throws Exception {
        sendAll(true, false);
        List<String> withQr = parts(sent.get(0));
        assertTrue(withQr.stream().anyMatch(p -> p.startsWith("image/png|inline|<attendance-qr>")), withQr.toString());
        assertTrue(withQr.stream().anyMatch(p -> p.startsWith("image/png|attachment|")), withQr.toString());
        assertTrue(htmlOf(sent.get(0)).contains("cid:attendance-qr"));

        sent.clear();
        participants.forEach(p -> p.setInvitationSentAt(null));
        sendAll(false, false);
        assertTrue(parts(sent.get(0)).stream().noneMatch(p -> p.startsWith("image/png")));
        assertFalse(htmlOf(sent.get(0)).contains("cid:"));
    }

    @Test
    void qrIsSilentlyDroppedWhenTheCourseHasAttendanceDisabled() throws Exception {
        course.setAttendanceEnabled(false);
        Result result = sendAll(true, false);
        assertFalse(result.includeQr());
        assertEquals(3, result.sent());
        assertTrue(parts(sent.get(0)).stream().noneMatch(p -> p.startsWith("image/png")));
    }

    @Test
    void eachQrEncodesThatParticipantsOwnToken() throws Exception {
        sendAll(true, false);
        assertEquals(3, sent.size());
        // Mỗi thư một ảnh khác nhau (mã ký riêng cho từng hồ sơ).
        List<Object> contents = new ArrayList<>();
        for (MimeMessage message : sent) {
            Multipart root = (Multipart) message.getContent();
            contents.add(java.util.Arrays.toString(inlineBytes(root)));
        }
        assertEquals(3, contents.stream().distinct().count());
    }

    private static byte[] inlineBytes(Multipart multipart) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.isMimeType("multipart/*")) {
                byte[] nested = inlineBytes((Multipart) part.getContent());
                if (nested != null) {
                    return nested;
                }
            } else if (part.isMimeType("image/png") && Part.INLINE.equals(part.getDisposition())) {
                return part.getInputStream().readAllBytes();
            }
        }
        return null;
    }

    // ---------- email thiếu / lỗi: ghi lại và vẫn gửi cho người khác ----------

    @Test
    void participantWithoutEmailIsNotedAndOthersStillGetTheirMail() throws Exception {
        byId("p-2").getLeader().setEmail(null);

        Result result = sendAll(false, false);

        assertEquals(3, result.total());
        assertEquals(2, result.sent());
        assertEquals(1, result.failed());
        Issue issue = result.issues().get(0);
        assertEquals("p-2", issue.participationId());
        assertEquals("Giuse", issue.holyName());
        assertEquals("Nguyễn Bình", issue.fullName());
        assertEquals("Hồ sơ chưa có email", issue.reason());
        assertEquals(List.of("an@mail.vn", "chi@mail.vn"), recipients());
        assertEquals("Hồ sơ chưa có email", byId("p-2").getInvitationError());
        assertNull(byId("p-2").getInvitationSentAt());
        assertNull(byId("p-1").getInvitationError());
    }

    @Test
    void malformedEmailIsNotedWithTheOffendingAddress() throws Exception {
        byId("p-1").getLeader().setEmail("khong-phai-email");
        byId("p-3").getLeader().setEmail("a@@b.vn");

        Result result = sendAll(false, false);

        assertEquals(1, result.sent());
        assertEquals(2, result.failed());
        assertTrue(result.issues().get(0).reason().contains("khong-phai-email"));
        assertEquals(List.of("binh@mail.vn"), recipients());
        verify(mailSender, org.mockito.Mockito.times(1)).send(any(MimeMessage.class));
    }

    @Test
    void everyParticipantWithoutAnEmailStillReturnsASuccessfulResult() {
        participants.forEach(p -> p.getLeader().setEmail(""));
        Result result = sendAll(false, false);
        assertEquals(0, result.sent());
        assertEquals(3, result.failed());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void smtpFailureForOneRecipientDoesNotStopTheOthers() throws Exception {
        doAnswer(invocation -> {
            MimeMessage message = invocation.getArgument(0);
            if ("binh@mail.vn".equals(recipient(message))) {
                throw new MailSendException("550 mailbox unavailable");
            }
            message.saveChanges();
            sent.add(message);
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        Result result = sendAll(false, false);

        assertEquals(2, result.sent());
        assertEquals(1, result.failed());
        assertTrue(result.issues().get(0).reason().startsWith("Không gửi được email: "));
        assertTrue(result.issues().get(0).reason().contains("550 mailbox unavailable"));
        assertEquals(List.of("an@mail.vn", "chi@mail.vn"), recipients());
        assertNull(byId("p-2").getInvitationSentAt());
        assertTrue(byId("p-2").getInvitationError().contains("550"));
        assertNotNull(byId("p-3").getInvitationSentAt());
    }

    @Test
    void unexpectedRuntimeFailureWhileComposingIsAlsoContained() {
        when(mailSender.createMimeMessage()).thenThrow(new IllegalStateException("không tạo được thư")).thenAnswer(
                invocation -> new MimeMessage(Session.getInstance(new Properties())));

        Result result = sendAll(false, false);

        assertEquals(2, result.sent());
        assertEquals(1, result.failed());
        assertTrue(result.issues().get(0).reason().startsWith("Không soạn được email: "));
    }

    @Test
    void veryLongSmtpErrorsAreTruncatedBeforeBeingStored() {
        doAnswer(invocation -> {
            throw new MailSendException("x".repeat(2000));
        }).when(mailSender).send(any(MimeMessage.class));

        sendAll(false, false);

        assertTrue(byId("p-1").getInvitationError().length() <= 480);
        assertTrue(byId("p-1").getInvitationError().startsWith("Không gửi được email: "));
    }

    @Test
    void laterSuccessClearsThePreviousError() {
        byId("p-1").setInvitationError("Lỗi cũ");
        sendAll(false, false);
        assertNull(byId("p-1").getInvitationError());
        assertNotNull(byId("p-1").getInvitationSentAt());
    }

    // ---------- không gửi trùng ----------

    @Test
    void alreadySentParticipantsAreSkippedUnlessResendIsRequested() throws Exception {
        OffsetDateTime before = OffsetDateTime.now().minusDays(1);
        byId("p-1").setInvitationSentAt(before);
        byId("p-2").setInvitationSentAt(before);

        Result skipping = sendAll(false, false);
        assertEquals(3, skipping.total());
        assertEquals(1, skipping.sent());
        assertEquals(2, skipping.skippedAlreadySent());
        assertEquals(0, skipping.failed());
        assertEquals(List.of("chi@mail.vn"), recipients());
        assertEquals(before, byId("p-1").getInvitationSentAt());

        sent.clear();
        Result resending = sendAll(false, true);
        assertEquals(3, resending.sent());
        assertEquals(0, resending.skippedAlreadySent());
        assertTrue(byId("p-1").getInvitationSentAt().isAfter(before));
    }

    @Test
    void failedResendKeepsTheOriginalSentTimeButRecordsTheError() {
        OffsetDateTime before = OffsetDateTime.now().minusDays(1);
        byId("p-1").setInvitationSentAt(before);
        byId("p-1").getLeader().setEmail("hong");

        sendAll(false, true);

        assertEquals(before, byId("p-1").getInvitationSentAt());
        assertTrue(byId("p-1").getInvitationError().startsWith("Email không hợp lệ"));
    }

    @Test
    void sendingTwiceInARowDoesNotSendTwice() throws Exception {
        sendAll(false, false);
        int firstRound = sent.size();
        Result second = sendAll(false, false);
        assertEquals(3, firstRound);
        assertEquals(3, sent.size());
        assertEquals(0, second.sent());
        assertEquals(3, second.skippedAlreadySent());
    }

    // ---------- chọn hồ sơ ----------

    @Test
    void sendsOnlyToTheSelectedParticipantsIgnoringDuplicates() throws Exception {
        Result result = service.sendInvitations("KH-1", List.of("p-3", "p-1", "p-3"), false, false);
        assertEquals(2, result.total());
        assertEquals(2, result.sent());
        assertEquals(List.of("an@mail.vn", "chi@mail.vn"), recipients());
    }

    @Test
    void emptySelectionMeansEveryone() {
        assertEquals(3, service.sendInvitations("KH-1", List.of(), false, false).sent());
    }

    @Test
    void unknownOrForeignIdsAreReportedButDoNotBlockTheRest() {
        Result result = service.sendInvitations("KH-1", List.of("p-1", "ghost", "other-course"), false, false);
        assertEquals(3, result.total());
        assertEquals(1, result.sent());
        assertEquals(2, result.failed());
        assertEquals("ghost", result.issues().get(0).participationId());
        assertTrue(result.issues().get(0).reason().contains("không thuộc danh sách tham dự"));
    }

    @Test
    void participantsAreMailedInTeamThenNameOrder() throws Exception {
        participants.clear();
        participants.add(TrainingTestFixtures.participation("p-z", TrainingTestFixtures.leader("G", "H", "Zeta", "z@mail.vn", parish), course, "Đội 2"));
        participants.add(TrainingTestFixtures.participation("p-a", TrainingTestFixtures.leader("G", "H", "Alpha", "a@mail.vn", parish), course, "Đội 10"));
        participants.add(TrainingTestFixtures.participation("p-b", TrainingTestFixtures.leader("G", "H", "Beta", "b@mail.vn", parish), course, "Đội 2"));

        sendAll(false, false);

        assertEquals(List.of("b@mail.vn", "z@mail.vn", "a@mail.vn"), recipients());
    }

    @Test
    void courseWithNoParticipantsSendsNothing() {
        participants.clear();
        Result result = sendAll(false, false);
        assertEquals(0, result.total());
        assertEquals(0, result.sent());
        verify(repository, never()).save(any());
    }

    // ---------- kiểm tra trước khi nhận yêu cầu gửi ----------

    private ApiException rejected(SendRequest request) {
        return assertThrows(ApiException.class, () -> service.validateForSend("KH-1", request));
    }

    @Test
    void validateAcceptsARegularRequest() {
        service.validateForSend("KH-1", new SendRequest(null, true, false));
        service.validateForSend("KH-1", new SendRequest(List.of("p-1"), false, true));
    }

    @Test
    void qrCannotBeRequestedWhenAttendanceIsDisabled() {
        course.setAttendanceEnabled(false);
        ApiException error = rejected(new SendRequest(null, true, false));
        assertEquals(409, error.getStatus().value());
        assertTrue(error.getMessage().contains("Phải bật điểm danh"));
        // Không xin QR thì vẫn gửi được.
        service.validateForSend("KH-1", new SendRequest(null, false, false));
    }

    @Test
    void cannotSendForDisabledOrEndedCourses() {
        course.setStatus(Boolean.FALSE);
        assertEquals(409, rejected(new SendRequest(null, false, false)).getStatus().value());

        course.setStatus(Boolean.TRUE);
        course.setStartDate(LocalDate.now().minusDays(9));
        course.setEndDate(LocalDate.now().minusDays(1));
        assertEquals(409, rejected(new SendRequest(null, false, false)).getStatus().value());
    }

    @Test
    void cannotSendWhenThereIsNobodyInTheParticipantList() {
        participants.clear();
        ApiException error = rejected(new SendRequest(null, false, false));
        assertEquals(400, error.getStatus().value());
        assertTrue(error.getMessage().contains("chưa có hồ sơ"));
    }

    @Test
    void pastCoursesAreRefused() {
        when(participantSupport.requireRegularCourse(eq("PAST"), any()))
                .thenThrow(new CommonValidationHelper(new securityUtils()).validationError("Khóa quá khứ không áp dụng gửi email thông tin tham dự"));
        assertEquals(400, assertThrows(ApiException.class, () -> service.validateForSend("PAST", new SendRequest(null, false, false))).getStatus().value());
    }
}
