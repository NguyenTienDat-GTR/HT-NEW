package phucuong.ht.modules.training.courseInvitation.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.courseAttendance.service.AttendanceTokenService;
import phucuong.ht.modules.training.courseInvitation.model.InvitationModels.Issue;
import phucuong.ht.modules.training.courseInvitation.model.InvitationModels.Result;
import phucuong.ht.modules.training.courseInvitation.model.InvitationModels.SendRequest;
import phucuong.ht.modules.training.courseInvitation.service.InvitationMailTemplate.Content;
import phucuong.ht.modules.training.courseInvitation.service.InvitationMailTemplate.CourseInfo;
import phucuong.ht.modules.training.courseInvitation.service.InvitationMailTemplate.Recipient;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipantSupport;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// Gửi email thông tin tham dự (tên thánh, họ tên, giáo xứ, giáo hạt, giáo phận, tên khóa, thời gian tổ chức và mã QR điểm danh
// nếu khóa bật điểm danh) cho hồ sơ trong danh sách tham dự. Hồ sơ không có email hoặc email sai định dạng / gửi lỗi được ghi lại
// (cột invitation_error + danh sách issues trả về) nhưng không chặn việc gửi cho các hồ sơ còn lại.
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseInvitationService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MAX_ERROR_LENGTH = 480;
    private static final int QR_MODULE_PIXELS = 8;
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+'-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$");

    private final JavaMailSender mailSender;
    private final PlatformTransactionManager transactionManager;
    private final CourseParticipantSupport participantSupport;
    private final CourseParticipationRepository courseParticipationRepository;
    private final AttendanceTokenService tokenService;
    private final CourseActivityGuard courseActivityGuard;
    private final CommonValidationHelper commonValidationHelper;

    @Value("${app.mail.from:${spring.mail.username:}}")
    private String from;

    // null = email hợp lệ; ngược lại là lý do (tiếng Việt) để hiển thị cho người dùng.
    public static String emailProblem(String email) {
        if (email == null || email.isBlank()) {
            return "Hồ sơ chưa có email";
        }
        String trimmed = email.trim();
        if (trimmed.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(trimmed).matches()) {
            return "Email không hợp lệ: " + (trimmed.length() > 80 ? trimmed.substring(0, 80) + "..." : trimmed);
        }
        return null;
    }

    public static boolean hasValidEmail(Leader leader) {
        return leader != null && emailProblem(leader.getEmail()) == null;
    }

    // Kiểm tra trước khi nhận yêu cầu gửi (đồng bộ, trả lỗi ngay cho người dùng thay vì để lỗi trong tác vụ nền).
    public void validateForSend(String courseCode, SendRequest request) {
        TrainingCourse course = participantSupport.requireRegularCourse(courseCode, "gửi email thông tin tham dự");
        courseActivityGuard.requireActive(course);
        courseActivityGuard.requireNotEnded(course);
        if (request.includeQr() && !Boolean.TRUE.equals(course.getAttendanceEnabled())) {
            throw commonValidationHelper.conflict("Phải bật điểm danh cho khóa huấn luyện thì mới gửi được mã QR trong email");
        }
        if (participantSupport.participantsOf(courseCode).isEmpty()) {
            throw commonValidationHelper.validationError("Khóa huấn luyện chưa có hồ sơ nào trong danh sách tham dự");
        }
    }

    private record Prepared(CourseInfo course, boolean includeQr, List<Recipient> recipients, List<Issue> issues, int skipped, int total) {
    }

    // Gửi cho các hồ sơ chọn (null / rỗng = toàn bộ danh sách tham dự). includeQr chỉ có hiệu lực khi khóa bật điểm danh;
    // resend = false thì bỏ qua hồ sơ đã được gửi thành công trước đó.
    public Result sendInvitations(String courseCode, List<String> participationIds, boolean includeQr, boolean resend) {
        Prepared prepared = new TransactionTemplate(transactionManager).execute(status ->
                prepare(courseCode, participationIds, includeQr, resend));
        List<Issue> issues = new ArrayList<>(prepared.issues());
        Map<String, String> failures = new LinkedHashMap<>();
        List<String> sentIds = new ArrayList<>();

        for (Recipient recipient : prepared.recipients()) {
            String problem = emailProblem(recipient.email());
            if (problem == null) {
                problem = trySend(prepared.course(), recipient, prepared.includeQr());
            }
            if (problem == null) {
                sentIds.add(recipient.participationId());
            } else {
                failures.put(recipient.participationId(), problem);
                issues.add(new Issue(recipient.participationId(), recipient.holyName(), recipient.fullName(), recipient.email(), problem));
            }
        }
        record(sentIds, failures);
        for (Issue issue : prepared.issues()) {
            log.debug("Bỏ qua hồ sơ {} khi gửi email thông tin tham dự: {}", issue.participationId(), issue.reason());
        }
        return new Result(prepared.total(), sentIds.size(), prepared.skipped(), issues.size(), prepared.includeQr(), issues);
    }

    private Prepared prepare(String courseCode, List<String> participationIds, boolean includeQr, boolean resend) {
        TrainingCourse course = participantSupport.requireRegularCourse(courseCode, "gửi email thông tin tham dự");
        boolean withQr = includeQr && Boolean.TRUE.equals(course.getAttendanceEnabled());
        List<CourseParticipation> participants = participantSupport.participantsOf(courseCode);
        Map<String, CourseParticipation> byId = new LinkedHashMap<>();
        participants.stream().sorted(CourseParticipantSupport.byTeamThenName()).forEach(p -> byId.put(p.getId(), p));

        List<Issue> issues = new ArrayList<>();
        List<CourseParticipation> selected = new ArrayList<>();
        if (participationIds == null || participationIds.isEmpty()) {
            selected.addAll(byId.values());
        } else {
            LinkedHashSet<String> wanted = new LinkedHashSet<>(participationIds);
            for (String id : wanted) {
                if (id == null || !byId.containsKey(id)) {
                    issues.add(new Issue(id, null, null, null, "Hồ sơ không thuộc danh sách tham dự của khóa"));
                }
            }
            // Giữ thứ tự gửi theo đội rồi tên, không theo thứ tự client chọn.
            byId.values().stream().filter(p -> wanted.contains(p.getId())).forEach(selected::add);
        }

        int skipped = 0;
        List<Recipient> recipients = new ArrayList<>();
        for (CourseParticipation participation : selected) {
            if (!resend && participation.getInvitationSentAt() != null) {
                skipped++;
                continue;
            }
            recipients.add(recipientOf(participation));
        }
        return new Prepared(courseInfo(course), withQr, recipients, issues, skipped, selected.size() + issues.size());
    }

    // Gửi một thư; trả null nếu thành công, ngược lại là lý do thất bại (không ném lỗi để các hồ sơ khác vẫn được gửi).
    private String trySend(CourseInfo course, Recipient recipient, boolean includeQr) {
        try {
            Content content = InvitationMailTemplate.build(course, recipient, includeQr);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            if (from != null && !from.isBlank()) {
                helper.setFrom(from);
            }
            helper.setTo(recipient.email().trim());
            helper.setSubject(content.subject());
            helper.setText(content.text(), content.html());
            if (includeQr) {
                byte[] png = tokenService.pngFor(recipient.participationId(), QR_MODULE_PIXELS);
                helper.addInline(InvitationMailTemplate.QR_CONTENT_ID, new ByteArrayResource(png), "image/png");
                helper.addAttachment("qr-diem-danh.png", new ByteArrayResource(png), "image/png");
            }
            mailSender.send(message);
            return null;
        } catch (MailException | MessagingException e) {
            log.warn("Không gửi được email thông tin tham dự tới {}: {}", recipient.email(), e.toString());
            return "Không gửi được email: " + shorten(rootMessage(e));
        } catch (RuntimeException e) {
            log.warn("Lỗi khi soạn email thông tin tham dự cho hồ sơ {}", recipient.participationId(), e);
            return "Không soạn được email: " + shorten(rootMessage(e));
        }
    }

    // Ghi kết quả vào hồ sơ: thành công xóa lỗi cũ và đặt giờ gửi; thất bại giữ nguyên giờ gửi cũ (nếu có) và lưu lý do.
    private void record(List<String> sentIds, Map<String, String> failures) {
        if (sentIds.isEmpty() && failures.isEmpty()) {
            return;
        }
        List<String> all = new ArrayList<>(sentIds);
        all.addAll(failures.keySet());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            OffsetDateTime now = OffsetDateTime.now();
            for (CourseParticipation participation : courseParticipationRepository.findAllByIdsWithLeaderAndCourse(all)) {
                if (sentIds.contains(participation.getId())) {
                    participation.setInvitationSentAt(now);
                    participation.setInvitationError(null);
                } else {
                    participation.setInvitationError(shorten(failures.get(participation.getId())));
                }
                courseParticipationRepository.save(participation);
            }
        });
    }

    private Recipient recipientOf(CourseParticipation participation) {
        Leader leader = participation.getLeader();
        var parish = leader.getParish();
        var deanery = parish == null ? null : parish.getDeanery();
        var diocese = deanery == null ? null : deanery.getDiocese();
        return new Recipient(participation.getId(), leader.getEmail(), leader.getHolyName(), CourseParticipantSupport.plainName(leader),
                parish == null ? null : parish.getName(), deanery == null ? null : deanery.getName(),
                diocese == null ? null : diocese.getName(), participation.getTeamName());
    }

    private CourseInfo courseInfo(TrainingCourse course) {
        String start = course.getStartDate() == null ? "" : course.getStartDate().format(DATE_FORMAT);
        String end = course.getEndDate() == null ? "" : course.getEndDate().format(DATE_FORMAT);
        String period = !start.isEmpty() && !end.isEmpty() ? ("từ " + start + " đến " + end) : (start + end);
        return new CourseInfo(course.getCourseCode(), course.getCourseName(), period, course.getLocation());
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static String shorten(String value) {
        if (value == null) {
            return null;
        }
        String single = value.replaceAll("\\s+", " ").trim();
        return single.length() > MAX_ERROR_LENGTH ? single.substring(0, MAX_ERROR_LENGTH) : single;
    }
}
