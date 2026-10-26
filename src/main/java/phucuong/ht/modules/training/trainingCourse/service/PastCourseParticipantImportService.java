package phucuong.ht.modules.training.trainingCourse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.excel.ExcelRowData;
import phucuong.ht.common.excel.ExcelSheetData;
import phucuong.ht.common.excel.ExcelWorkbookReader;
import phucuong.ht.common.excel.ImportErrors;
import phucuong.ht.common.excel.ImportSummary;
import phucuong.ht.common.excel.ImportTemplates;
import phucuong.ht.common.excel.job.JobProgress;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.CacheNames;
import phucuong.ht.config.EvictCaches;
import phucuong.ht.modules.certificate.model.entity.Certificate;
import phucuong.ht.modules.certificate.repository.CertificateRepository;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.service.LeaderImportService;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.training.CourseActivityGuard;
import phucuong.ht.modules.training.TrainingWorkflowSupport;
import phucuong.ht.modules.training.courseParticipation.model.entity.CourseParticipation;
import phucuong.ht.modules.training.courseParticipation.repository.CourseParticipationRepository;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipationService;
import phucuong.ht.modules.training.enums.CourseFinalResult;
import phucuong.ht.modules.training.enums.CourseResult;
import phucuong.ht.modules.training.enums.ParticipationStatus;
import phucuong.ht.modules.training.enums.WorkflowApprovalStatus;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Import danh sách tham dự (kèm điểm và kết quả) vào khóa huấn luyện quá khứ. Huynh trưởng được tìm theo email hoặc
 * số điện thoại (đều duy nhất), không dùng mã UUID. Có bất kỳ dòng lỗi nào thì không lưu gì.
 * Học viên Đạt của khóa có cấp độ đạt được được chuyển sang chờ duyệt chứng nhận.
 * Khóa thường cũng import được: chỉ gồm huynh trưởng (không có điểm / kết quả), hồ sơ vào thẳng danh sách tham dự (đã duyệt),
 * người import phải là người duyệt đăng ký của đơn vị sở hữu khóa.
 */
@Service
@RequiredArgsConstructor
public class PastCourseParticipantImportService {

    public static final String COL_EMAIL = "Email";
    public static final String COL_PHONE = "Số điện thoại";
    public static final String COL_SCORE = "Tổng điểm";
    public static final String COL_RESULT = "Kết quả";
    public static final String COL_REVIEW = "Nhận xét";
    public static final String COL_EXCEPTION_REASON = "Lí do ngoại lệ";
    public static final String COL_TEAM = "Tên đội";
    // Ghi chú đăng ký của hồ sơ (tùy chọn, có thể bỏ trống hoặc không có cột).
    public static final String COL_NOTE = "Ghi chú";
    private static final int MAX_NOTE = 1000;
    private static final int MAX_TEAM_NAME = 150;

    private static final BigDecimal MAX_SCORE = new BigDecimal("1000");

    private final TrainingCourseRepository trainingCourseRepository;
    private final CourseParticipationRepository courseParticipationRepository;
    private final LeaderRepository leaderRepository;
    private final ParishRepository parishRepository;
    private final LeaderImportService leaderImportService;
    private final PastTrainingCourseService pastTrainingCourseService;
    private final CourseParticipationService courseParticipationService;
    private final CertificateRepository certificateRepository;
    private final TrainingWorkflowSupport trainingWorkflowSupport;
    private final CourseActivityGuard courseActivityGuard;
    private final CommonValidationHelper commonValidationHelper;
    private final LeaderLevelService leaderLevelService;
    // Khóa hàng khóa huấn luyện: import / xóa học viên / sửa khóa quá khứ chạy tuần tự, không dẫm lên nhau (tuỳ chọn để test đơn vị không cần).
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.common.concurrency.EntityLockService entityLockService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.common.security.UnitOperationalStatusService unitOperationalStatusService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.modules.training.courseInvitation.service.CourseInvitationAutoTrigger invitationAutoTrigger;

    // leader: huynh trưởng đã có hồ sơ; newLeader: thông tin để tạo mới (đúng một trong hai khác null).
    private record Parsed(ExcelRowData row, Leader leader, LeaderImportService.NewLeader newLeader, CourseResult result, BigDecimal score, String review,
                          boolean exception, String exceptionReason, String teamName, String note) {
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    @EvictCaches.Analytics
    @EvictCaches.Leaders
    @Transactional
    public ImportSummary importParticipants(String courseCode, java.nio.file.Path file, Authentication authentication, JobProgress progress) {
        return importParticipants(courseCode, file, authentication, progress, false);
    }

    /**
     * withTeam = true: import danh sách chia đội của khóa thường. File giống danh sách tham dự nhưng thêm cột "Tên đội", không có sheet Ngoại lệ,
     * mọi hồ sơ vào thẳng danh sách tham dự (đã duyệt đăng ký, không kiểm tra điều kiện của khóa) và gắn đội.
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    @EvictCaches.Analytics
    @EvictCaches.Leaders
    @Transactional
    public ImportSummary importParticipants(String courseCode, java.nio.file.Path file, Authentication authentication, JobProgress progress, boolean withTeam) {
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        boolean past = course.isPast();
        if (withTeam && past) {
            throw commonValidationHelper.validationError("Khóa quá khứ không áp dụng chia đội");
        }
        requireImportAllowed(course, authentication);
        lockCourse(course);
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người import danh sách tham dự");

        List<Parsed> parsed;
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(file)) {
            ExcelSheetData sheet = reader.firstSheet();
            ImportErrors errors = new ImportErrors();
            // Mọi cột đều bắt buộc có trong file, trừ Cấp độ và Nhận xét; khóa thường không có Tổng điểm / Kết quả.
            sheet.requireColumns(errors, COL_EMAIL, COL_PHONE,
                    LeaderImportService.COL_HOLY_NAME, LeaderImportService.COL_FULL_NAME, LeaderImportService.COL_BIRTH_DATE,
                    LeaderImportService.COL_GENDER, LeaderImportService.COL_DEANERY, LeaderImportService.COL_PARISH);
            if (past) {
                sheet.requireColumns(errors, COL_SCORE, COL_RESULT);
            }
            if (withTeam) {
                sheet.requireColumns(errors, COL_TEAM);
            }
            errors.throwIfAny();
            // Khóa thường có thêm sheet "Ngoại lệ" (huynh trưởng không đủ điều kiện, chờ duyệt đăng ký); sheet này có thể không có hoặc để trống.
            ExcelSheetData exceptionSheet = past || withTeam ? null : reader.sheet(ImportTemplates.EXCEPTION_SHEET);
            if (exceptionSheet != null) {
                exceptionSheet.requireColumns(errors, COL_EMAIL, COL_PHONE, COL_EXCEPTION_REASON,
                        LeaderImportService.COL_HOLY_NAME, LeaderImportService.COL_FULL_NAME, LeaderImportService.COL_BIRTH_DATE,
                        LeaderImportService.COL_GENDER, LeaderImportService.COL_DEANERY, LeaderImportService.COL_PARISH);
                errors.throwIfAny();
            }
            if (sheet.rows().isEmpty() && (exceptionSheet == null || exceptionSheet.rows().isEmpty())) {
                throw commonValidationHelper.validationError("File Excel không có dòng dữ liệu nào");
            }
            parsed = parse(sheet, exceptionSheet, course, errors, past, withTeam);
            errors.throwIfAny();
        }
        progress.report(0, parsed.size());

        OffsetDateTime now = OffsetDateTime.now();
        LocalDate registrationDate = course.getStartDate() != null ? course.getStartDate() : LocalDate.of(course.getHostYear(), 1, 1);
        boolean certificateRequired = trainingWorkflowSupport.requiresCertificate(course);
        int created = 0;
        int newLeaders = 0;
        int exceptions = 0;
        // Khóa thường: hồ sơ vào thẳng danh sách tham dự coi như vừa được duyệt (khóa bật tự gửi email sẽ gửi thông tin tham dự).
        List<CourseParticipation> directlyApproved = new java.util.ArrayList<>();
        for (Parsed item : parsed) {
            Leader leader = item.leader();
            if (leader == null) {
                // Huynh trưởng chưa có hồ sơ: tạo ngay trong cùng transaction (lỗi ở bất kỳ dòng nào thì rollback cả hồ sơ lẫn danh sách tham dự).
                leader = leaderRepository.save(leaderImportService.buildLeader(item.newLeader(), actor, now));
                newLeaders++;
            }
            CourseParticipation participation = new CourseParticipation();
            participation.setLeader(leader);
            participation.setCourse(course);
            participation.setEligible(Boolean.TRUE);
            participation.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
            participation.setApprovedBy(actor);
            boolean passed = false;
            if (past) {
                participation.setRegistrationDate(registrationDate);
                participation.setTotalScore(item.score());
                participation.setReview(item.review());
                participation.setResult(item.result());
                passed = item.result() == CourseResult.COMPLETED;
                participation.setParticipationStatus(passed ? ParticipationStatus.COMPLETED : ParticipationStatus.ATTENDED);
                if (!passed) {
                    participation.setFinalResult(CourseFinalResult.FAILED);
                    participation.setCertificateApprovalStatus(WorkflowApprovalStatus.NOT_REQUIRED);
                } else if (certificateRequired) {
                    // Chờ duyệt chứng nhận; kết quả chung (Đạt) chốt khi chứng nhận được duyệt.
                    participation.setCertificateApprovalStatus(WorkflowApprovalStatus.PENDING);
                    participation.setCertificateRequestedAt(now);
                } else {
                    participation.setFinalResult(CourseFinalResult.PASSED);
                    participation.setCertificateApprovalStatus(WorkflowApprovalStatus.NOT_REQUIRED);
                }
            } else if (item.exception()) {
                // Ngoại lệ: chờ duyệt đăng ký như hồ sơ gửi từ màn Đăng ký (cờ ngoại lệ + lí do), người duyệt chọn cấp độ đạt được khi duyệt.
                exceptions++;
                participation.setEligible(Boolean.FALSE);
                participation.setExceptionReason(item.exceptionReason());
                participation.setApprovalStatus(WorkflowApprovalStatus.PENDING);
                participation.setApprovedBy(null);
                participation.setRegistrationDate(LocalDate.now());
                participation.setResult(CourseResult.UNCOMPLETED);
                participation.setParticipationStatus(ParticipationStatus.REGISTERED);
                participation.setParishSubmittedAt(now);
                participation.setCertificateApprovalStatus(WorkflowApprovalStatus.NOT_REQUIRED);
            } else {
                // Khóa thường: vào thẳng danh sách tham dự (như hồ sơ đã duyệt), điểm được chấm sau ở tab Chấm điểm.
                participation.setRegistrationDate(LocalDate.now());
                participation.setResult(CourseResult.UNCOMPLETED);
                participation.setParticipationStatus(ParticipationStatus.ATTENDED);
                participation.setCertificateApprovalStatus(WorkflowApprovalStatus.NOT_REQUIRED);
                participation.setTeamName(item.teamName());
                directlyApproved.add(participation);
            }
            participation.setRegistrationNote(item.note());
            participation.setStatus(Boolean.TRUE);
            participation.setCreatedAt(now);
            participation.setUpdatedAt(now);
            participation.setCreatedBy(actor);
            participation.setUpdatedBy(actor);
            courseParticipationRepository.save(participation);
            if (passed && certificateRequired) {
                courseParticipationService.ensureSuggestedCertificateExists(participation, actor);
            }
            created++;
            if (created % 200 == 0) {
                progress.report(created, parsed.size());
            }
        }
        if (invitationAutoTrigger != null) {
            invitationAutoTrigger.afterApproved(directlyApproved);
        }
        String message = withTeam ? "Đã import " + created + " học viên đã chia đội vào khóa \"" + course.getCourseName() + "\""
                + (newLeaders > 0 ? ", trong đó tạo mới " + newLeaders + " hồ sơ huynh trưởng" : "")
                : "Đã import " + created + " học viên vào khóa \"" + course.getCourseName() + "\""
                + (exceptions > 0 ? ", trong đó " + exceptions + " hồ sơ ngoại lệ chờ duyệt đăng ký" : "")
                + (newLeaders > 0 ? ", trong đó tạo mới " + newLeaders + " hồ sơ huynh trưởng" : "");
        return new ImportSummary(created, message);
    }

    // Xóa một học viên khỏi khóa quá khứ (sửa nhầm khi import). Học viên đã có chứng nhận được duyệt thì phải thu hồi trước.
    // Khác khóa thường: ở khóa quá khứ hồ sơ được xóa hẳn (không có luồng duyệt lại), kèm chứng nhận đề xuất chưa duyệt.
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    @EvictCaches.Analytics
    @Transactional
    public void removeParticipant(String courseCode, String participationId, Authentication authentication) {
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        pastTrainingCourseService.requirePastCourseOfActor(course, authentication);
        lockCourse(course);
        courseActivityGuard.requireCourseEnabled(course);
        CourseParticipation participation = courseParticipationRepository.findByIdWithLeaderAndCourse(participationId)
                .filter(item -> item.getCourse() != null && courseCode.equals(item.getCourse().getCourseCode()))
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy hồ sơ tham dự " + participationId + " trong khóa này"));
        java.util.Optional<Certificate> certificate = certificateRepository.findByLeaderIdAndCourseCode(participation.getLeader().getId(), courseCode);
        if (participation.getCertificateApprovalStatus() == WorkflowApprovalStatus.APPROVED
                || certificate.map(item -> item.getApprovalStatus() == WorkflowApprovalStatus.APPROVED || item.getApprovalStatus() == WorkflowApprovalStatus.REVOKED).orElse(false)) {
            throw commonValidationHelper.conflict("Học viên đã có chứng nhận được duyệt nên không xóa được khỏi khóa");
        }
        certificate.ifPresent(certificateRepository::delete);
        courseParticipationRepository.delete(participation);
    }

    // Kiểm tra nhanh lúc tạo job (đúng khóa quá khứ của giáo phận mình, khóa còn bật) để báo lỗi ngay thay vì đợi job chạy.
    @Transactional(readOnly = true)
    public void precheck(String courseCode, Authentication authentication) {
        precheck(courseCode, authentication, false);
    }

    @Transactional(readOnly = true)
    public void precheck(String courseCode, Authentication authentication, boolean withTeam) {
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        if (withTeam && course.isPast()) {
            throw commonValidationHelper.validationError("Khóa quá khứ không áp dụng chia đội");
        }
        requireImportAllowed(course, authentication);
    }

    // Khóa quá khứ: đúng đơn vị của tài khoản có quyền create_past. Khóa thường: người duyệt đăng ký của đơn vị sở hữu khóa,
    // khóa còn hoạt động và chưa kết thúc.
    private void requireImportAllowed(TrainingCourse course, Authentication authentication) {
        if (course.isPast()) {
            pastTrainingCourseService.requirePastCourseOfActor(course, authentication);
            courseActivityGuard.requireCourseEnabled(course);
            return;
        }
        courseActivityGuard.requireActive(course);
        courseActivityGuard.requireNotEnded(course);
        if (!courseParticipationService.canApproveCourse(course, authentication)) {
            throw commonValidationHelper.forbidden("Không có quyền nhập danh sách tham dự cho khóa huấn luyện này");
        }
    }

    private void lockCourse(TrainingCourse course) {
        if (entityLockService != null) {
            entityLockService.lock(course);
        }
    }

    private List<Parsed> parse(ExcelSheetData sheet, ExcelSheetData exceptionSheet, TrainingCourse course, ImportErrors errors, boolean past, boolean withTeam) {
        // Gộp dòng của cả hai sheet để tra cứu huynh trưởng / chỉ mục giáo xứ / cấp độ đúng một lần cho cả file.
        List<ExcelRowData> allRows = new ArrayList<>(sheet.rows());
        int mainCount = allRows.size();
        if (exceptionSheet != null) {
            allRows.addAll(exceptionSheet.rows());
        }
        LeaderLevelService.LevelOrder courseLevels = past || withTeam ? null : leaderLevelService.order(trainingWorkflowSupport.dioceseIdOf(course));
        // Tra cứu huynh trưởng một lần cho cả file.
        Set<String> emails = new HashSet<>();
        Set<String> phones = new HashSet<>();
        for (ExcelRowData row : allRows) {
            String email = row.text(COL_EMAIL);
            if (email != null) {
                emails.add(email.toLowerCase(Locale.ROOT));
            }
            String phone = row.phone(COL_PHONE);
            if (phone != null) {
                phones.add(phone);
            }
        }
        Map<String, Leader> byEmail = new HashMap<>();
        for (Leader leader : leaderRepository.findAllByEmailsIgnoreCase(emails)) {
            byEmail.put(leader.getEmail().toLowerCase(Locale.ROOT), leader);
        }
        Map<String, Leader> byPhone = new HashMap<>();
        for (Leader leader : leaderRepository.findAllByPhoneNumbers(phones)) {
            byPhone.put(leader.getPhoneNumber(), leader);
        }
        Set<UUID> alreadyInCourse = new HashSet<>();
        for (CourseParticipation existing : courseParticipationRepository.findAllByCourseCode(course.getCourseCode())) {
            if (existing.getLeader() != null) {
                alreadyInCourse.add(existing.getLeader().getId());
            }
        }
        // Giáo hạt / giáo xứ phải có sẵn trong hệ thống; giáo xứ ngoài giáo hạt (khóa giáo hạt) hoặc giáo phận (khóa giáo phận) của khóa bị báo ở parishProblems.
        LeaderImportService.ParishIndex parishIndex = leaderImportService.indexParishes(parishRepository.findAllWithDeaneryAndDiocese());
        Map<UUID, String> parishProblems = new HashMap<>();
        Map<UUID, LeaderLevelService.LevelOrder> levelCache = new HashMap<>();
        Set<String> plannedEmails = new HashSet<>();
        Set<String> plannedPhones = new HashSet<>();

        List<Parsed> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (int rowIndex = 0; rowIndex < allRows.size(); rowIndex++) {
            ExcelRowData row = allRows.get(rowIndex);
            boolean exceptionRow = rowIndex >= mainCount;
            int before = errorCount(errors);
            String email = row.text(COL_EMAIL);
            String phone = row.phone(COL_PHONE);
            Leader leader = null;
            LeaderImportService.NewLeader newLeader = null;
            if (email == null || phone == null) {
                row.error(errors, email == null ? COL_EMAIL : COL_PHONE, "Bắt buộc nhập");
            } else {
                Leader foundByEmail = email == null ? null : byEmail.get(email.toLowerCase(Locale.ROOT));
                Leader foundByPhone = phone == null ? null : byPhone.get(phone);
                if (foundByEmail != null && foundByPhone != null && !foundByEmail.getId().equals(foundByPhone.getId())) {
                    row.error(errors, COL_EMAIL + " / " + COL_PHONE, "Email và số điện thoại thuộc hai huynh trưởng khác nhau");
                } else {
                    // Đã có hồ sơ (theo email hoặc số điện thoại) thì chỉ thêm vào khóa, không tạo mới và không sửa hồ sơ.
                    leader = foundByEmail != null ? foundByEmail : foundByPhone;
                    newLeader = planNewLeader(row, errors, course, parishIndex, parishProblems, levelCache, plannedEmails, plannedPhones, leader != null);
                }
            }
            if (leader != null) {
                if (!leaderInCourseScope(leader, course)) {
                    row.error(errors, COL_EMAIL + " / " + COL_PHONE, course.getDeanery() != null
                            ? "Huynh trưởng không thuộc giáo hạt của khóa" : "Huynh trưởng không thuộc giáo phận của khóa");
                } else if (alreadyInCourse.contains(leader.getId())) {
                    row.error(errors, COL_EMAIL + " / " + COL_PHONE, "Huynh trưởng đã có hồ sơ đăng ký / tham dự của khóa");
                } else if (!seen.add(leader.getId())) {
                    row.error(errors, COL_EMAIL + " / " + COL_PHONE, "Huynh trưởng xuất hiện nhiều lần trong file");
                }
            }
            CourseResult courseResult = null;
            BigDecimal score = null;
            String review = null;
            if (past) {
                courseResult = parseResult(row, errors);
                score = row.decimal(errors, COL_SCORE, true);
                if (score != null && (score.signum() < 0 || score.compareTo(MAX_SCORE) > 0)) {
                    row.error(errors, COL_SCORE, "Điểm phải từ 0 đến " + MAX_SCORE.toPlainString());
                }
                review = row.text(COL_REVIEW);
                if (review != null && review.length() > 2000) {
                    row.error(errors, COL_REVIEW, "Nhận xét tối đa 2000 ký tự");
                }
            }
            String note = row.text(COL_NOTE);
            if (note != null && note.length() > MAX_NOTE) {
                row.error(errors, COL_NOTE, "Ghi chú tối đa " + MAX_NOTE + " ký tự");
            }
            String exceptionReason = null;
            String teamName = null;
            if (withTeam) {
                teamName = row.requiredText(errors, COL_TEAM);
                if (teamName != null && teamName.length() > MAX_TEAM_NAME) {
                    row.error(errors, COL_TEAM, "Tên đội tối đa " + MAX_TEAM_NAME + " ký tự");
                }
            }
            if (!past && !withTeam) {
                if (exceptionRow) {
                    exceptionReason = row.requiredText(errors, COL_EXCEPTION_REASON);
                }
                // Phân loại theo điều kiện của khóa: sheet chính chỉ nhận người đủ điều kiện, sheet Ngoại lệ chỉ nhận người chưa đủ (giống màn Đăng ký).
                if (errorCount(errors) == before && (leader != null || newLeader != null)) {
                    String level = leader != null ? leader.getLeaderLevel() : newLeader.level();
                    boolean eligible = courseParticipationService.isEligible(level,
                            leader != null ? leader.getBirthDate() : newLeader.birthDate(), leader != null ? leader.getId() : null, course);
                    if (!exceptionRow && !eligible) {
                        row.error(errors, COL_EMAIL + " / " + COL_PHONE, "Huynh trưởng không đủ điều kiện của khóa, hãy đưa vào sheet \"" + ImportTemplates.EXCEPTION_SHEET + "\"");
                    } else if (exceptionRow && eligible) {
                        row.error(errors, COL_EMAIL + " / " + COL_PHONE, "Huynh trưởng đã đủ điều kiện của khóa, hãy đưa vào sheet \"" + ImportTemplates.PARTICIPANTS_SHEET + "\"");
                    } else if (exceptionRow && courseParticipationService.exceedsNearestAchievedLevel(level, course, courseLevels)) {
                        row.error(errors, COL_EMAIL + " / " + COL_PHONE, "Huynh trưởng có cấp độ cao hơn cấp độ đạt được gần nhất của loại khóa nên không thể là ngoại lệ");
                    }
                }
            }
            if (errorCount(errors) == before && (leader != null || newLeader != null) && (!past || courseResult != null)) {
                result.add(new Parsed(row, leader, newLeader, courseResult, score, review, exceptionRow, exceptionReason, teamName, note));
            }
        }
        return result;
    }

    // Mọi dòng đều phải nhập đủ thông tin huynh trưởng (kể cả khi đã có hồ sơ). Huynh trưởng đã có hồ sơ chỉ được thêm vào khóa,
    // thông tin trong dòng chỉ để kiểm tra, không dùng để sửa hồ sơ; huynh trưởng chưa có hồ sơ thì được tạo mới từ dòng này.
    private LeaderImportService.NewLeader planNewLeader(ExcelRowData row, ImportErrors errors, TrainingCourse course, LeaderImportService.ParishIndex parishIndex,
                                                        Map<UUID, String> parishProblems, Map<UUID, LeaderLevelService.LevelOrder> levelCache,
                                                        Set<String> plannedEmails, Set<String> plannedPhones, boolean existingLeader) {
        LeaderImportService.NewLeader data = leaderImportService.parseNewLeader(row, errors, parishIndex,
                parish -> parishProblems.computeIfAbsent(parish.getId(), id -> parishProblem(parish, course)), levelCache);
        if (data == null || existingLeader) {
            return null;
        }
        // Cùng một huynh trưởng mới xuất hiện ở hai dòng của file (cùng email hoặc cùng số điện thoại).
        if (!plannedEmails.add(data.email().toLowerCase(Locale.ROOT)) || (data.phone() != null && !plannedPhones.add(data.phone()))) {
            row.error(errors, COL_EMAIL + " / " + COL_PHONE, "Huynh trưởng xuất hiện nhiều lần trong file");
            return null;
        }
        return data;
    }

    private String parishProblem(Parish parish, TrainingCourse course) {
        if (!parishInCourseScope(parish, course)) {
            return course.getDeanery() != null ? "Giáo xứ không thuộc giáo hạt của khóa" : "Giáo xứ không thuộc giáo phận của khóa";
        }
        return operationalProblem(parish);
    }

    // Chuỗi rỗng nghĩa là giáo xứ đang hoạt động; ngược lại là lý do không tạo được huynh trưởng trong giáo xứ này.
    private String operationalProblem(Parish parish) {
        if (unitOperationalStatusService == null) {
            return "";
        }
        try {
            unitOperationalStatusService.requireUnitOperational(phucuong.ht.common.enums.UnitType.PARISH, parish.getId());
            return "";
        } catch (phucuong.ht.modules.auth.exception.ApiException exception) {
            return exception.getMessage() == null ? "Giáo xứ đang bị khóa" : exception.getMessage();
        }
    }

    // Khóa giáo hạt: huynh trưởng / giáo xứ phải thuộc giáo hạt của khóa; khóa giáo phận: thuộc giáo phận của khóa.
    private boolean parishInCourseScope(Parish parish, TrainingCourse course) {
        if (parish.getDeanery() == null) {
            return false;
        }
        if (course.getDeanery() != null) {
            return course.getDeanery().getId().equals(parish.getDeanery().getId());
        }
        return course.getDiocese() != null && parish.getDeanery().getDiocese() != null
                && course.getDiocese().getId().equals(parish.getDeanery().getDiocese().getId());
    }

    private boolean leaderInCourseScope(Leader leader, TrainingCourse course) {
        return leader.getParish() != null && parishInCourseScope(leader.getParish(), course);
    }

    private CourseResult parseResult(ExcelRowData row, ImportErrors errors) {
        String text = row.requiredText(errors, COL_RESULT);
        if (text == null) {
            return null;
        }
        String normalized = ExcelWorkbookReader.normalize(text);
        return switch (normalized) {
            case "dat", "hoan thanh", "passed", "pass" -> CourseResult.COMPLETED;
            case "khong dat", "chua dat", "chua hoan thanh", "truot", "failed", "fail" -> CourseResult.UNCOMPLETED;
            default -> {
                row.error(errors, COL_RESULT, "Kết quả phải là \"Đạt\" hoặc \"Không đạt\"");
                yield null;
            }
        };
    }

    // Số lỗi hiện có; dùng để biết dòng đang xử lý có phát sinh lỗi mới hay không.
    private int errorCount(ImportErrors errors) {
        return errors.count();
    }
}
