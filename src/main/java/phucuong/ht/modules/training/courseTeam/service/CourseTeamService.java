package phucuong.ht.modules.training.courseTeam.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.EvictCaches;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipationService;
import phucuong.ht.modules.training.courseTeam.model.request.TeamAssignRequest;
import phucuong.ht.modules.training.courseTeam.model.request.TeamDivideRequest;
import phucuong.ht.modules.training.courseTeam.model.response.TeamOption;
import phucuong.ht.modules.training.courseTeam.model.response.TeamOverviewResponse;
import phucuong.ht.modules.training.courseTeam.model.response.TeamSummary;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chia đội cho khóa huấn luyện thường: chia hồ sơ trong danh sách tham dự (đã duyệt đăng ký, chưa bị loại) vào các đội,
 * cân bằng tỉ lệ nam / nữ, tùy chọn giới hạn số đơn vị (giáo hạt hoặc giáo xứ) trong cùng một đội. Chia lại sẽ ghi đè đội cũ.
 */
@Service
@RequiredArgsConstructor
public class CourseTeamService {

    private static final int MAX_TEAM_NAME = 150;
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final String UNIT_DEANERY = "DEANERY";
    private static final String UNIT_PARISH = "PARISH";

    private final TrainingCourseRepository trainingCourseRepository;
    private final CourseParticipationRepository courseParticipationRepository;
    private final DeaneryRepository deaneryRepository;
    private final ParishRepository parishRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final CourseActivityGuard courseActivityGuard;
    private final CourseParticipationService courseParticipationService;
    private final CommonValidationHelper commonValidationHelper;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.common.concurrency.EntityLockService entityLockService;

    @Transactional(readOnly = true)
    public TeamOverviewResponse overview(String courseCode) {
        TrainingCourse course = requireRegularCourse(courseCode);
        List<CourseParticipation> participants = participantsOf(courseCode);
        boolean deaneryManaged = trainingWorkflowSupport.isDeaneryManaged(course);
        int male = 0;
        int female = 0;
        int other = 0;
        for (CourseParticipation participant : participants) {
            switch (genderOf(participant)) {
                case MALE -> male++;
                case FEMALE -> female++;
                default -> other++;
            }
        }
        Map<String, List<CourseParticipation>> byTeam = new LinkedHashMap<>();
        int divided = 0;
        for (CourseParticipation participant : participants) {
            if (participant.getTeamName() != null && !participant.getTeamName().isBlank()) {
                byTeam.computeIfAbsent(participant.getTeamName(), key -> new ArrayList<>()).add(participant);
                divided++;
            }
        }
        List<TeamSummary> teams = byTeam.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(CourseTeamService::naturalCompare))
                .map(entry -> summarize(entry.getKey(), entry.getValue(), deaneryManaged))
                .toList();
        return new TeamOverviewResponse(courseCode, participants.size(), male, female, other, divided,
                deaneryManaged ? UNIT_PARISH : UNIT_DEANERY, activeUnitCount(course, deaneryManaged), teams);
    }

    @Transactional(readOnly = true)
    public List<TeamOption> options(String courseCode) {
        requireRegularCourse(courseCode);
        return participantsOf(courseCode).stream()
                .map(CourseParticipation::getTeamName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted(CourseTeamService::naturalCompare)
                .map(TeamOption::new)
                .toList();
    }

    @EvictCaches.Courses
    @Transactional
    public TeamOverviewResponse divide(String courseCode, TeamDivideRequest request, Authentication authentication) {
        TrainingCourse course = requireRegularCourse(courseCode);
        requireManageable(course, authentication);
        lock(course);
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người chia đội");
        boolean deaneryManaged = trainingWorkflowSupport.isDeaneryManaged(course);

        int teamCount = request.getTeamCount();
        List<String> names = resolveNames(request, teamCount);
        Integer maxUnits = null;
        if (request.getAssignmentMode() == TeamDivideRequest.AssignmentMode.UNIT_LIMIT) {
            if (request.getMaxUnitsPerTeam() == null) {
                throw commonValidationHelper.validationError("Hãy nhập số " + unitLabel(deaneryManaged) + " tối đa trong cùng một đội");
            }
            int active = activeUnitCount(course, deaneryManaged);
            if (request.getMaxUnitsPerTeam() > active) {
                throw commonValidationHelper.validationError("Số " + unitLabel(deaneryManaged) + " tối đa mỗi đội không được lớn hơn tổng số "
                        + unitLabel(deaneryManaged) + " đang hoạt động (" + active + ")");
            }
            maxUnits = request.getMaxUnitsPerTeam();
        }

        List<CourseParticipation> participants = participantsOf(courseCode);
        List<TeamDivisionPlanner.Member> members = new ArrayList<>();
        Map<String, CourseParticipation> byId = new LinkedHashMap<>();
        for (CourseParticipation participant : participants) {
            byId.put(participant.getId(), participant);
            members.add(new TeamDivisionPlanner.Member(participant.getId(), unitKeyOf(participant, deaneryManaged), genderOf(participant)));
        }
        List<List<String>> plan;
        try {
            plan = TeamDivisionPlanner.plan(members, teamCount, request.getTeamSize(), maxUnits, new Random());
        } catch (IllegalArgumentException exception) {
            throw commonValidationHelper.validationError(exception.getMessage());
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (CourseParticipation participant : participants) {
            participant.setTeamName(null);
        }
        for (int team = 0; team < plan.size(); team++) {
            for (String participationId : plan.get(team)) {
                CourseParticipation participant = byId.get(participationId);
                participant.setTeamName(names.get(team));
                participant.setUpdatedAt(now);
                participant.setUpdatedBy(actor);
            }
        }
        // Hồ sơ đã bị loại khỏi danh sách tham dự không còn thuộc đội nào.
        clearOutsideParticipants(courseCode, participants, actor, now);
        return overview(courseCode);
    }

    // Gán đội thủ công: tên đội mới được tạo ngay khi có hồ sơ dùng; hồ sơ phải nằm trong danh sách tham dự của khóa.
    @EvictCaches.Courses
    @Transactional
    public TeamOverviewResponse assign(String courseCode, TeamAssignRequest request, Authentication authentication) {
        TrainingCourse course = requireRegularCourse(courseCode);
        requireManageable(course, authentication);
        lock(course);
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người gán đội");
        Map<String, CourseParticipation> byId = new java.util.HashMap<>();
        participantsOf(courseCode).forEach(participant -> byId.put(participant.getId(), participant));
        // Tên đội không phân biệt hoa thường: dùng lại đúng cách viết của đội đã có để không tách thành hai đội.
        Map<String, String> canonical = new java.util.HashMap<>();
        byId.values().stream().map(CourseParticipation::getTeamName).filter(name -> name != null && !name.isBlank())
                .forEach(name -> canonical.putIfAbsent(name.toLowerCase(Locale.ROOT), name));
        OffsetDateTime now = OffsetDateTime.now();
        for (TeamAssignRequest.Item item : request.getAssignments()) {
            CourseParticipation participant = byId.get(item.getParticipationId());
            if (participant == null) {
                throw commonValidationHelper.validationError("Hồ sơ " + item.getParticipationId() + " không nằm trong danh sách tham dự của khóa");
            }
            String name = item.getTeamName() == null ? "" : item.getTeamName().trim().replaceAll("\\s+", " ");
            participant.setTeamName(name.isEmpty() ? null : canonical.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> name));
            participant.setUpdatedAt(now);
            participant.setUpdatedBy(actor);
        }
        return overview(courseCode);
    }

    @EvictCaches.Courses
    @Transactional
    public TeamOverviewResponse clear(String courseCode, Authentication authentication) {
        TrainingCourse course = requireRegularCourse(courseCode);
        requireManageable(course, authentication);
        lock(course);
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người xóa chia đội");
        OffsetDateTime now = OffsetDateTime.now();
        for (CourseParticipation participation : courseParticipationRepository.findAllByCourseCode(courseCode)) {
            if (participation.getTeamName() != null) {
                participation.setTeamName(null);
                participation.setUpdatedAt(now);
                participation.setUpdatedBy(actor);
            }
        }
        return overview(courseCode);
    }

    private void clearOutsideParticipants(String courseCode, List<CourseParticipation> participants, String actor, OffsetDateTime now) {
        Set<String> inList = new HashSet<>();
        participants.forEach(participant -> inList.add(participant.getId()));
        for (CourseParticipation participation : courseParticipationRepository.findAllByCourseCode(courseCode)) {
            if (!inList.contains(participation.getId()) && participation.getTeamName() != null) {
                participation.setTeamName(null);
                participation.setUpdatedAt(now);
                participation.setUpdatedBy(actor);
            }
        }
    }

    private TrainingCourse requireRegularCourse(String courseCode) {
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        if (course.isPast()) {
            throw commonValidationHelper.validationError("Khóa quá khứ không áp dụng chia đội");
        }
        return course;
    }

    private void requireManageable(TrainingCourse course, Authentication authentication) {
        courseActivityGuard.requireActive(course);
        courseActivityGuard.requireNotEnded(course);
        if (!courseParticipationService.canApproveCourse(course, authentication)) {
            throw commonValidationHelper.forbidden("Không có quyền chia đội cho khóa huấn luyện này");
        }
    }

    private void lock(TrainingCourse course) {
        if (entityLockService != null) {
            entityLockService.lock(course);
        }
    }

    // Hồ sơ nằm trong danh sách tham dự: đã duyệt đăng ký, chưa bị từ chối / loại và còn hiệu lực.
    private List<CourseParticipation> participantsOf(String courseCode) {
        return courseParticipationRepository.findAllByCourseCode(courseCode).stream()
                .filter(participation -> participation.getApprovalStatus() == WorkflowApprovalStatus.APPROVED)
                .filter(participation -> participation.getParticipationStatus() != ParticipationStatus.REJECTED)
                .filter(participation -> !Boolean.FALSE.equals(participation.getStatus()))
                .filter(participation -> participation.getLeader() != null)
                .toList();
    }

    private List<String> resolveNames(TeamDivideRequest request, int teamCount) {
        List<String> names = new ArrayList<>();
        if (request.getNamingMode() == TeamDivideRequest.NamingMode.SEQUENTIAL) {
            for (int i = 1; i <= teamCount; i++) {
                names.add("Đội " + i);
            }
            return names;
        }
        List<String> raw = request.getTeamNames() == null ? List.of() : request.getTeamNames();
        if (raw.size() != teamCount) {
            throw commonValidationHelper.validationError("Cần nhập đủ tên cho " + teamCount + " đội");
        }
        Set<String> seen = new HashSet<>();
        for (String value : raw) {
            String name = value == null ? "" : value.trim().replaceAll("\\s+", " ");
            if (name.isEmpty()) {
                throw commonValidationHelper.validationError("Tên đội không được để trống");
            }
            if (name.length() > MAX_TEAM_NAME) {
                throw commonValidationHelper.validationError("Tên đội tối đa " + MAX_TEAM_NAME + " ký tự");
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw commonValidationHelper.validationError("Tên đội \"" + name + "\" bị trùng");
            }
            names.add(name);
        }
        return names;
    }

    private int activeUnitCount(TrainingCourse course, boolean deaneryManaged) {
        if (deaneryManaged) {
            return course.getDeanery() == null ? 0 : parishRepository.countActiveByDeaneryId(course.getDeanery().getId());
        }
        java.util.UUID dioceseId = trainingWorkflowSupport.dioceseIdOf(course);
        return dioceseId == null ? 0 : deaneryRepository.countActiveByDioceseId(dioceseId);
    }

    private static String unitLabel(boolean deaneryManaged) {
        return deaneryManaged ? "giáo xứ" : "giáo hạt";
    }

    private static String unitKeyOf(CourseParticipation participant, boolean deaneryManaged) {
        Leader leader = participant.getLeader();
        if (leader.getParish() == null) {
            return "";
        }
        if (deaneryManaged) {
            return String.valueOf(leader.getParish().getId());
        }
        return leader.getParish().getDeanery() == null ? "" : String.valueOf(leader.getParish().getDeanery().getId());
    }

    private static TeamDivisionPlanner.Gender genderOf(CourseParticipation participant) {
        Leader leader = participant.getLeader();
        if (leader == null || leader.getGender() == null) {
            return TeamDivisionPlanner.Gender.OTHER;
        }
        return switch (leader.getGender()) {
            case NAM -> TeamDivisionPlanner.Gender.MALE;
            case NU -> TeamDivisionPlanner.Gender.FEMALE;
        };
    }

    private static TeamSummary summarize(String teamName, List<CourseParticipation> members, boolean deaneryManaged) {
        int male = 0;
        int female = 0;
        int other = 0;
        Set<String> units = new HashSet<>();
        for (CourseParticipation member : members) {
            switch (genderOf(member)) {
                case MALE -> male++;
                case FEMALE -> female++;
                default -> other++;
            }
            units.add(unitKeyOf(member, deaneryManaged));
        }
        return new TeamSummary(teamName, members.size(), male, female, other, units.size());
    }

    // So sánh tự nhiên: "Đội 2" đứng trước "Đội 10".
    static int naturalCompare(String left, String right) {
        Matcher leftMatcher = DIGITS.matcher(left);
        Matcher rightMatcher = DIGITS.matcher(right);
        if (leftMatcher.find() && rightMatcher.find()) {
            String leftPrefix = left.substring(0, leftMatcher.start());
            String rightPrefix = right.substring(0, rightMatcher.start());
            int prefix = leftPrefix.compareToIgnoreCase(rightPrefix);
            if (prefix != 0) {
                return prefix;
            }
            int number = new java.math.BigInteger(leftMatcher.group()).compareTo(new java.math.BigInteger(rightMatcher.group()));
            if (number != 0) {
                return number;
            }
        }
        return Comparator.<String>naturalOrder().compare(left.toLowerCase(Locale.ROOT), right.toLowerCase(Locale.ROOT));
    }
}
