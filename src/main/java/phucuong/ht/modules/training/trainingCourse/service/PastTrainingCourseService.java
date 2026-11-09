package phucuong.ht.modules.training.trainingCourse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.validation.AccountScopeValidationHelper;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.CacheNames;
import phucuong.ht.config.EvictCaches;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.enums.CourseOrganizingStatus;
import phucuong.ht.modules.training.enums.CourseRegistrationStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.model.request.PastTrainingCourseRequest;
import phucuong.ht.modules.training.trainingCourse.model.response.TrainingCourseResponse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;
import phucuong.ht.modules.training.trainingCourse.security.TrainingCourseAccessEvaluator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Tạo / sửa khóa huấn luyện trong quá khứ. Chỉ tài khoản giáo phận được super-admin cấp quyền
 * {@value TrainingCourseAccessEvaluator#CREATE_PAST_DIOCESE} mới dùng được; khóa thuộc đúng giáo phận của tài khoản.
 * Khóa không có loại khóa và thời gian đăng ký, đã kết thúc ngay khi tạo, chỉ dùng để lưu lịch sử tham dự / chứng nhận.
 */
@Service
@RequiredArgsConstructor
public class PastTrainingCourseService {

    private final TrainingCourseRepository trainingCourseRepository;
    private final CourseParticipationRepository courseParticipationRepository;
    private final DioceseRepository dioceseRepository;
    private final phucuong.ht.modules.deanery.repository.DeaneryRepository deaneryRepository;
    private final phucuong.ht.common.utils.securityUtils securityUtils;
    private final AccountScopeValidationHelper accountScopeValidationHelper;
    private final CommonValidationHelper commonValidationHelper;
    private final LeaderLevelService leaderLevelService;
    private final CourseActivityGuard courseActivityGuard;
    private final TrainingCourseService trainingCourseService;
    // Khóa hàng khóa huấn luyện khi sửa: không sửa trong lúc đang import / xóa học viên (tuỳ chọn để test đơn vị không cần).
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.common.concurrency.EntityLockService entityLockService;

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true)
    })
    @EvictCaches.Analytics
    @Transactional
    public TrainingCourseResponse create(PastTrainingCourseRequest request, Authentication authentication) {
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người tạo khóa huấn luyện");
        Owner owner = resolveActorOwner(authentication);
        UUID dioceseId = owner.dioceseId();
        OffsetDateTime now = OffsetDateTime.now();

        TrainingCourse course = new TrainingCourse();
        course.setCourseCode(UUID.randomUUID().toString());
        course.setPastCourse(Boolean.TRUE);
        if (owner.deaneryId() != null) {
            // Admin giáo hạt: khóa thuộc giáo hạt (như khóa giáo hạt thường: gắn giáo hạt, không gắn trực tiếp giáo phận).
            course.setDeanery(deaneryRepository.getRequired(owner.deaneryId()));
            course.setDiocese(null);
        } else {
            course.setDiocese(dioceseRepository.getRequired(dioceseId));
            course.setDeanery(null);
        }
        course.setCourseType(null);
        course.setRegistrationEnabled(Boolean.FALSE);
        apply(course, request, dioceseId);
        course.setYearsActive(0);
        course.setStatus(Boolean.TRUE);
        course.setScoreVisible(Boolean.TRUE.equals(request.getScoreVisible()));
        course.setAutoApproveEligible(Boolean.FALSE);
        course.setAttendanceEnabled(Boolean.FALSE);
        course.setAutoSendInvitation(Boolean.FALSE);
        // Đã kết thúc ngay: không đăng ký, không chấm điểm, không kết thúc lại.
        course.setScoreLocked(Boolean.TRUE);
        course.setFinishedAt(finishedAt(course, now));
        course.setFinishedBy(actor);
        course.setRegistrationStatus(CourseRegistrationStatus.ENDED);
        course.setOrganizingStatus(CourseOrganizingStatus.ENDED);
        course.setCreatedAt(now);
        course.setUpdatedAt(now);
        course.setCreatedBy(actor);
        course.setUpdatedBy(actor);
        trainingCourseRepository.save(course);
        return trainingCourseService.loadDetail(course.getCourseCode());
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true)
    })
    @EvictCaches.Analytics
    @EvictCaches.Certificates
    @Transactional
    public TrainingCourseResponse update(String courseCode, PastTrainingCourseRequest request, Authentication authentication) {
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        requirePastCourseOfActor(course, authentication);
        if (entityLockService != null) {
            entityLockService.lock(course);
        }
        courseActivityGuard.requireCourseEnabled(course);

        boolean hasParticipations = courseParticipationRepository.existsByCourseCode(courseCode);
        UUID dioceseId = ownerDioceseId(course);
        String newLevel = normalizeLevel(request.getResultingLevel(), dioceseId);
        BigDecimal newPassing = request.getPassingScore() == null ? BigDecimal.ZERO : request.getPassingScore();
        if (hasParticipations && (!Objects.equals(course.getHostYear(), request.getHostYear())
                || !Objects.equals(course.getResultingLevel(), newLevel)
                || course.getPassingScore().compareTo(newPassing) != 0)) {
            throw commonValidationHelper.conflict("Khóa đã có danh sách tham dự nên không đổi được năm tổ chức, cấp độ đạt được và điểm đạt");
        }
        apply(course, request, dioceseId);
        if (request.getScoreVisible() != null) {
            course.setScoreVisible(request.getScoreVisible());
        }
        OffsetDateTime now = OffsetDateTime.now();
        course.setFinishedAt(finishedAt(course, now));
        course.setUpdatedAt(now);
        course.setUpdatedBy(commonValidationHelper.requireAuditActor(authentication, "Không xác định được người cập nhật khóa huấn luyện"));
        return trainingCourseService.loadDetail(courseCode);
    }

    /** Đơn vị sở hữu khóa quá khứ của tài khoản: giáo phận (deaneryId null) hoặc giáo hạt; dioceseId luôn là giáo phận chứa đơn vị đó. */
    public record Owner(UUID dioceseId, UUID deaneryId) {
    }

    /** Đơn vị sở hữu dùng khi tạo khóa mới: ưu tiên giáo phận, thiếu thì giáo hạt. */
    public Owner resolveActorOwner(Authentication authentication) {
        if (securityUtils.hasAuthority(authentication, TrainingCourseAccessEvaluator.CREATE_PAST_DIOCESE)) {
            return new Owner(accountScopeValidationHelper.requireActiveDioceseId(authentication, TrainingCourseAccessEvaluator.CREATE_PAST_DIOCESE), null);
        }
        return deaneryOwner(authentication);
    }

    private Owner deaneryOwner(Authentication authentication) {
        UUID deaneryId = accountScopeValidationHelper.requireActiveDeaneryId(authentication, TrainingCourseAccessEvaluator.CREATE_PAST_DEANERY);
        var deanery = deaneryRepository.getRequired(deaneryId);
        if (deanery.getDiocese() == null) {
            throw commonValidationHelper.conflict("Giáo hạt chưa thuộc giáo phận nào");
        }
        return new Owner(deanery.getDiocese().getId(), deaneryId);
    }

    /** Mọi đơn vị sở hữu khóa quá khứ mà tài khoản được thao tác (giáo phận và/hoặc giáo hạt, theo từng quyền). */
    private java.util.List<Owner> resolveActorOwners(Authentication authentication) {
        java.util.List<Owner> owners = new java.util.ArrayList<>();
        if (securityUtils.hasAuthority(authentication, TrainingCourseAccessEvaluator.CREATE_PAST_DIOCESE)) {
            owners.add(new Owner(accountScopeValidationHelper.requireActiveDioceseId(authentication, TrainingCourseAccessEvaluator.CREATE_PAST_DIOCESE), null));
        }
        if (securityUtils.hasAuthority(authentication, TrainingCourseAccessEvaluator.CREATE_PAST_DEANERY)) {
            if (owners.isEmpty()) {
                owners.add(deaneryOwner(authentication));
            } else {
                try {
                    owners.add(deaneryOwner(authentication));
                } catch (phucuong.ht.common.exception.ApiException noDeanery) {
                    // Có quyền giáo phận nhưng không có giáo hạt hiệu lực: chỉ dùng phạm vi giáo phận.
                }
            }
        }
        if (owners.isEmpty()) {
            owners.add(resolveActorOwner(authentication));
        }
        return owners;
    }

    /** Giáo phận chứa đơn vị sở hữu khóa (khóa giáo hạt lấy theo giáo hạt). */
    public UUID ownerDioceseId(TrainingCourse course) {
        if (course.getDeanery() != null) {
            return course.getDeanery().getDiocese().getId();
        }
        return course.getDiocese().getId();
    }

    // Khóa phải là khóa quá khứ thuộc đúng đơn vị của tài khoản: admin giáo phận chỉ thao tác khóa của giáo phận mình,
    // admin giáo hạt chỉ thao tác khóa của giáo hạt mình.
    public TrainingCourse requirePastCourseOfActor(TrainingCourse course, Authentication authentication) {
        if (!course.isPast()) {
            throw commonValidationHelper.conflict("Đây không phải khóa huấn luyện quá khứ");
        }
        java.util.List<Owner> owners = resolveActorOwners(authentication);
        boolean owned = owners.stream().anyMatch(owner -> owner.deaneryId() != null
                ? course.getDeanery() != null && owner.deaneryId().equals(course.getDeanery().getId())
                : course.getDeanery() == null && course.getDiocese() != null && owner.dioceseId().equals(course.getDiocese().getId()));
        if (!owned) {
            throw commonValidationHelper.forbidden(owners.size() > 1
                    ? "Không thể thao tác khóa huấn luyện ngoài phạm vi của tài khoản"
                    : owners.get(0).deaneryId() != null
                    ? "Không thể thao tác khóa huấn luyện ngoài giáo hạt hiện tại"
                    : "Không thể thao tác khóa huấn luyện ngoài giáo phận hiện tại");
        }
        return course;
    }

    private void apply(TrainingCourse course, PastTrainingCourseRequest request, UUID dioceseId) {
        String name = commonValidationHelper.normalizeRequiredText(request.getCourseName());
        if (name == null || name.isBlank()) {
            throw commonValidationHelper.validationError("Tên khóa huấn luyện là bắt buộc");
        }
        int currentYear = LocalDate.now().getYear();
        if (request.getHostYear() > currentYear) {
            throw commonValidationHelper.validationError("Khóa quá khứ không được có năm tổ chức sau năm hiện tại (" + currentYear + ")");
        }
        if (request.getStartDate() != null && request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw commonValidationHelper.validationError("Ngày kết thúc không được trước ngày bắt đầu");
        }
        LocalDate today = LocalDate.now();
        if ((request.getStartDate() != null && request.getStartDate().isAfter(today))
                || (request.getEndDate() != null && request.getEndDate().isAfter(today))) {
            throw commonValidationHelper.validationError("Khóa quá khứ không được có ngày sau ngày hiện tại");
        }
        course.setCourseName(name);
        course.setHostYear(request.getHostYear());
        course.setStartDate(request.getStartDate());
        course.setEndDate(request.getEndDate());
        course.setLocation(commonValidationHelper.normalizeOptionalText(request.getLocation()));
        course.setAdditionalNotes(commonValidationHelper.normalizeOptionalText(request.getAdditionalNotes()));
        course.setPassingScore(request.getPassingScore() == null ? BigDecimal.ZERO : request.getPassingScore());
        course.setResultingLevel(normalizeLevel(request.getResultingLevel(), dioceseId));
        course.setRegistrationStartAt(null);
        course.setRegistrationEndAt(null);
    }

    // Cấp độ phải có trong danh mục của giáo phận; để trống hoặc NONE nghĩa là khóa không có cấp độ (không có chứng nhận).
    private String normalizeLevel(String level, UUID dioceseId) {
        String checked = leaderLevelService.requireExistingOrNull(level, "Cấp độ đạt được", dioceseId);
        return checked == null || LeaderLevelService.NONE.equals(checked) ? null : checked;
    }

    // Thời điểm "kết thúc" của khóa: cuối ngày kết thúc, hoặc cuối năm tổ chức nếu không nhập thời gian.
    private OffsetDateTime finishedAt(TrainingCourse course, OffsetDateTime now) {
        LocalDate date = course.getEndDate() != null ? course.getEndDate()
                : course.getStartDate() != null ? course.getStartDate()
                : LocalDate.of(course.getHostYear(), 12, 31);
        if (date.isAfter(LocalDate.now())) {
            date = LocalDate.now();
        }
        return date.atTime(23, 59).atZone(TrainingCourseStatusSupport.ZONE).toOffsetDateTime();
    }
}
