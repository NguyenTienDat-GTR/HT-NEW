package phucuong.ht.modules.training.courseAttendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.EvictCaches;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.InvitationFilter;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.ParticipantDetail;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.ParticipantQuery;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.ParticipantRow;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.ScanResult;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.ScanStatus;
import phucuong.ht.modules.training.courseAttendance.model.AttendanceModels.Summary;
import phucuong.ht.modules.training.courseInvitation.service.CourseInvitationService;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipantSupport;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

// Điểm danh khóa huấn luyện bằng mã QR của hồ sơ (hoặc thủ công). Chỉ khóa thường có bật điểm danh, còn hoạt động và chưa kết thúc.
// Mỗi hồ sơ điểm danh một lần cho cả khóa; quét lại trả "đã điểm danh" kèm giờ điểm danh đầu tiên, không ghi đè.
@Service
@RequiredArgsConstructor
public class CourseAttendanceService {

    private final CourseParticipantSupport participantSupport;
    private final CourseParticipationRepository courseParticipationRepository;
    private final AttendanceTokenService tokenService;
    private final CourseActivityGuard courseActivityGuard;
    private final CommonValidationHelper commonValidationHelper;
    private final phucuong.ht.common.utils.AuditActorNameResolver auditActorNameResolver;

    @Transactional(readOnly = true)
    public Summary summary(String courseCode) {
        TrainingCourse course = participantSupport.requireRegularCourse(courseCode, "điểm danh");
        List<CourseParticipation> participants = participantSupport.participantsOf(courseCode);
        int checkedIn = 0;
        int sent = 0;
        int failed = 0;
        int withoutEmail = 0;
        for (CourseParticipation participant : participants) {
            if (participant.getCheckedInAt() != null) {
                checkedIn++;
            }
            if (participant.getInvitationSentAt() != null) {
                sent++;
            }
            if (participant.getInvitationError() != null) {
                failed++;
            }
            if (!CourseInvitationService.hasValidEmail(participant.getLeader())) {
                withoutEmail++;
            }
        }
        return new Summary(courseCode, Boolean.TRUE.equals(course.getAttendanceEnabled()),
                Boolean.TRUE.equals(course.getAutoSendInvitation()), participants.size(), checkedIn,
                participants.size() - checkedIn, sent, participants.size() - sent, failed, withoutEmail);
    }

    // Danh sách hồ sơ trong danh sách tham dự kèm tình trạng email / điểm danh, lọc và phân trang phía server.
    @Transactional(readOnly = true)
    public Page<ParticipantRow> participants(String courseCode, ParticipantQuery query) {
        participantSupport.requireRegularCourse(courseCode, "điểm danh");
        String keyword = fold(query.search());
        String team = fold(query.team());
        List<ParticipantRow> matched = participantSupport.participantsOf(courseCode).stream()
                .sorted(CourseParticipantSupport.byTeamThenName())
                .filter(p -> team.isEmpty() || fold(p.getTeamName()).equals(team))
                .filter(p -> query.checkedIn() == null || query.checkedIn() == (p.getCheckedInAt() != null))
                .filter(p -> matchesInvitation(p, query.invitation()))
                .map(this::toRow)
                .filter(row -> keyword.isEmpty() || fold(String.join(" ", nullToEmpty(row.holyName()), nullToEmpty(row.fullName()),
                        nullToEmpty(row.parishName()), nullToEmpty(row.deaneryName()), nullToEmpty(row.teamName()),
                        nullToEmpty(row.email()))).contains(keyword))
                .toList();
        int size = Math.max(1, Math.min(query.size(), 100));
        int page = Math.max(0, query.page());
        int from = (int) Math.min((long) page * size, matched.size());
        int to = Math.min(from + size, matched.size());
        return new PageImpl<>(matched.subList(from, to), PageRequest.of(page, size), matched.size());
    }

    // Chi tiết một hồ sơ trong danh sách tham dự của khóa (người tạo, người duyệt, người điểm danh hiện kèm tên).
    @Transactional(readOnly = true)
    public ParticipantDetail participantDetail(String courseCode, String participationId) {
        participantSupport.requireRegularCourse(courseCode, "điểm danh");
        CourseParticipation p = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .filter(found -> found.getCourse() != null && courseCode.equals(found.getCourse().getCourseCode()))
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy hồ sơ " + participationId + " trong khóa"));
        requireParticipant(p);
        Leader leader = p.getLeader();
        var parish = leader.getParish();
        var deanery = parish == null ? null : parish.getDeanery();
        var diocese = deanery == null ? null : deanery.getDiocese();
        return new ParticipantDetail(p.getId(), courseCode, p.getCourse().getCourseName(), Boolean.TRUE.equals(p.getCourse().getAttendanceEnabled()),
                leader.getHolyName(), CourseParticipantSupport.plainName(leader), leader.getGender() == null ? null : leader.getGender().name(),
                leader.getBirthDate(), leader.getPhoneNumber(), parish == null ? null : parish.getName(), deanery == null ? null : deanery.getName(),
                diocese == null ? null : diocese.getName(), p.getTeamName(), leader.getLeaderLevel(),
                leader.getEmail(), CourseInvitationService.emailProblem(leader.getEmail()), p.getInvitationSentAt(), p.getInvitationError(),
                p.getCheckedInAt(), p.getCheckedInBy(), nameOf(p.getCheckedInBy()),
                p.getRegistrationDate(), p.getParishSubmittedAt(),
                p.getApprovalStatus() == null ? null : p.getApprovalStatus().name(), p.getApprovedBy(), nameOf(p.getApprovedBy()),
                p.getCreatedAt(), p.getCreatedBy(), nameOf(p.getCreatedBy()), p.getUpdatedAt(), p.getUpdatedBy(), nameOf(p.getUpdatedBy()));
    }

    private String nameOf(String actor) {
        return actor == null || actor.isBlank() ? null : auditActorNameResolver.resolve(actor);
    }

    private boolean matchesInvitation(CourseParticipation participation, InvitationFilter filter) {
        if (filter == null) {
            return true;
        }
        return switch (filter) {
            case SENT -> participation.getInvitationSentAt() != null;
            case PENDING -> participation.getInvitationSentAt() == null;
            case FAILED -> participation.getInvitationError() != null;
            case NO_EMAIL -> !CourseInvitationService.hasValidEmail(participation.getLeader());
        };
    }

    private ParticipantRow toRow(CourseParticipation participation) {
        Leader leader = participation.getLeader();
        var parish = leader.getParish();
        var deanery = parish == null ? null : parish.getDeanery();
        return new ParticipantRow(participation.getId(), leader.getHolyName(), CourseParticipantSupport.plainName(leader),
                parish == null ? null : parish.getName(), deanery == null ? null : deanery.getName(), participation.getTeamName(),
                leader.getEmail(), CourseInvitationService.emailProblem(leader.getEmail()), participation.getInvitationSentAt(),
                participation.getInvitationError(), participation.getCheckedInAt(), participation.getCheckedInBy());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    // Chữ thường, bỏ dấu tiếng Việt để tìm kiếm thân thiện.
    private static String fold(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).trim();
    }

    // Tra cứu mã QR để người dùng xác nhận trước khi điểm danh: kiểm tra giống scan nhưng không ghi gì.
    @Transactional(readOnly = true)
    public ScanResult lookup(String courseCode, String token) {
        return resolve(courseCode, token).result(false);
    }

    private record Resolved(CourseParticipation participation, CourseAttendanceService service) {
        ScanResult result(boolean unused) {
            return service.result(participation.getCheckedInAt() != null ? ScanStatus.ALREADY_CHECKED_IN : ScanStatus.READY, participation);
        }
    }

    private Resolved resolve(String courseCode, String token) {
        TrainingCourse course = requireAttendanceOpen(courseCode);
        String participationId = tokenService.verify(token)
                .orElseThrow(() -> commonValidationHelper.validationError("Mã QR không hợp lệ hoặc không phải mã điểm danh của hệ thống"));
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy hồ sơ của mã QR này"));
        if (participation.getCourse() == null || !course.getCourseCode().equals(participation.getCourse().getCourseCode())) {
            throw commonValidationHelper.validationError("Mã QR này thuộc khóa huấn luyện khác");
        }
        requireParticipant(participation);
        return new Resolved(participation, this);
    }

    // Quét mã QR: kiểm tra chữ ký, hồ sơ thuộc đúng khóa và còn trong danh sách tham dự rồi ghi giờ điểm danh.
    @EvictCaches.Courses
    @Transactional
    public ScanResult scan(String courseCode, String token, Authentication authentication) {
        CourseParticipation participation = resolve(courseCode, token).participation();
        if (participation.getCheckedInAt() != null) {
            return result(ScanStatus.ALREADY_CHECKED_IN, participation);
        }
        markCheckedIn(participation, authentication);
        return result(ScanStatus.CHECKED_IN, participation);
    }

    // Điểm danh / bỏ điểm danh thủ công cho một hồ sơ (khi quên mang thẻ hoặc điểm danh nhầm).
    @EvictCaches.Courses
    @Transactional
    public ScanResult setCheckedIn(String courseCode, String participationId, boolean checkedIn, Authentication authentication) {
        TrainingCourse course = requireAttendanceOpen(courseCode);
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy hồ sơ " + participationId));
        if (participation.getCourse() == null || !course.getCourseCode().equals(participation.getCourse().getCourseCode())) {
            throw commonValidationHelper.validationError("Hồ sơ không thuộc khóa huấn luyện này");
        }
        requireParticipant(participation);
        if (checkedIn) {
            if (participation.getCheckedInAt() != null) {
                return result(ScanStatus.ALREADY_CHECKED_IN, participation);
            }
            markCheckedIn(participation, authentication);
            return result(ScanStatus.CHECKED_IN, participation);
        }
        participation.setCheckedInAt(null);
        participation.setCheckedInBy(null);
        participation.setUpdatedAt(OffsetDateTime.now());
        participation.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người điểm danh"));
        courseParticipationRepository.save(participation);
        return result(ScanStatus.CHECK_IN_CANCELLED, participation);
    }

    private TrainingCourse requireAttendanceOpen(String courseCode) {
        TrainingCourse course = participantSupport.requireRegularCourse(courseCode, "điểm danh");
        courseActivityGuard.requireActive(course);
        courseActivityGuard.requireNotEnded(course);
        if (!Boolean.TRUE.equals(course.getAttendanceEnabled())) {
            throw commonValidationHelper.conflict("Khóa huấn luyện chưa bật điểm danh");
        }
        return course;
    }

    private void requireParticipant(CourseParticipation participation) {
        if (!CourseParticipantSupport.isParticipant(participation)) {
            throw commonValidationHelper.conflict("Hồ sơ không còn trong danh sách tham dự của khóa");
        }
    }

    private void markCheckedIn(CourseParticipation participation, Authentication authentication) {
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người điểm danh");
        OffsetDateTime now = OffsetDateTime.now();
        participation.setCheckedInAt(now);
        participation.setCheckedInBy(actor);
        participation.setUpdatedAt(now);
        participation.setUpdatedBy(actor);
        courseParticipationRepository.save(participation);
    }

    private ScanResult result(ScanStatus status, CourseParticipation participation) {
        Leader leader = participation.getLeader();
        var parish = leader.getParish();
        var deanery = parish == null ? null : parish.getDeanery();
        return new ScanResult(status, participation.getId(),
                leader.getHolyName(), CourseParticipantSupport.plainName(leader),
                parish == null ? null : parish.getName(), deanery == null ? null : deanery.getName(),
                participation.getTeamName(), participation.getCheckedInAt());
    }
}
