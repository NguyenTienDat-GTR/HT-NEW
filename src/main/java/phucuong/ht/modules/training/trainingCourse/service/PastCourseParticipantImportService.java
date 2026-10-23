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
 */
@Service
@RequiredArgsConstructor
public class PastCourseParticipantImportService {

    public static final String COL_EMAIL = "Email";
    public static final String COL_PHONE = "Số điện thoại";
    public static final String COL_SCORE = "Tổng điểm";
    public static final String COL_RESULT = "Kết quả";
    public static final String COL_REVIEW = "Nhận xét";

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
    // Khóa hàng khóa huấn luyện: import / xóa học viên / sửa khóa quá khứ chạy tuần tự, không dẫm lên nhau (tuỳ chọn để test đơn vị không cần).
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.common.concurrency.EntityLockService entityLockService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private phucuong.ht.common.security.UnitOperationalStatusService unitOperationalStatusService;

    // leader: huynh trưởng đã có hồ sơ; newLeader: thông tin để tạo mới (đúng một trong hai khác null).
    private record Parsed(ExcelRowData row, Leader leader, LeaderImportService.NewLeader newLeader, CourseResult result, BigDecimal score, String review) {
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
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        pastTrainingCourseService.requirePastCourseOfActor(course, authentication);
        lockCourse(course);
        courseActivityGuard.requireCourseEnabled(course);
        String actor = commonValidationHelper.requireAuditActor(authentication, "Không xác định được người import danh sách tham dự");

        List<Parsed> parsed;
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(file)) {
            ExcelSheetData sheet = reader.firstSheet();
            ImportErrors errors = new ImportErrors();
            // Mọi cột đều bắt buộc có trong file, trừ Cấp độ và Nhận xét.
            sheet.requireColumns(errors, COL_EMAIL, COL_PHONE, COL_SCORE, COL_RESULT,
                    LeaderImportService.COL_HOLY_NAME, LeaderImportService.COL_FULL_NAME, LeaderImportService.COL_BIRTH_DATE,
                    LeaderImportService.COL_GENDER, LeaderImportService.COL_DEANERY, LeaderImportService.COL_PARISH);
            errors.throwIfAny();
            if (sheet.rows().isEmpty()) {
                throw commonValidationHelper.validationError("File Excel không có dòng dữ liệu nào");
            }
            parsed = parse(sheet, course, errors);
            errors.throwIfAny();
        }
        progress.report(0, parsed.size());

        OffsetDateTime now = OffsetDateTime.now();
        LocalDate registrationDate = course.getStartDate() != null ? course.getStartDate() : LocalDate.of(course.getHostYear(), 1, 1);
        boolean certificateRequired = trainingWorkflowSupport.requiresCertificate(course);
        int created = 0;
        int newLeaders = 0;
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
            participation.setRegistrationDate(registrationDate);
            participation.setEligible(Boolean.TRUE);
            participation.setApprovalStatus(WorkflowApprovalStatus.APPROVED);
            participation.setApprovedBy(actor);
            participation.setTotalScore(item.score());
            participation.setReview(item.review());
            participation.setResult(item.result());
            boolean passed = item.result() == CourseResult.COMPLETED;
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
        String message = "Đã import " + created + " học viên vào khóa \"" + course.getCourseName() + "\""
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
        TrainingCourse course = trainingCourseRepository.findByCodeWithType(courseCode)
                .orElseThrow(() -> commonValidationHelper.notFound("Không tìm thấy khóa huấn luyện " + courseCode));
        pastTrainingCourseService.requirePastCourseOfActor(course, authentication);
        courseActivityGuard.requireCourseEnabled(course);
    }

    private void lockCourse(TrainingCourse course) {
        if (entityLockService != null) {
            entityLockService.lock(course);
        }
    }

    private List<Parsed> parse(ExcelSheetData sheet, TrainingCourse course, ImportErrors errors) {
        // Tra cứu huynh trưởng một lần cho cả file.
        Set<String> emails = new HashSet<>();
        Set<String> phones = new HashSet<>();
        for (ExcelRowData row : sheet.rows()) {
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
        for (ExcelRowData row : sheet.rows()) {
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
                    row.error(errors, COL_EMAIL + " / " + COL_PHONE, "Huynh trưởng đã có trong danh sách tham dự của khóa");
                } else if (!seen.add(leader.getId())) {
                    row.error(errors, COL_EMAIL + " / " + COL_PHONE, "Huynh trưởng xuất hiện nhiều lần trong file");
                }
            }
            CourseResult courseResult = parseResult(row, errors);
            BigDecimal score = row.decimal(errors, COL_SCORE, true);
            if (score != null && (score.signum() < 0 || score.compareTo(MAX_SCORE) > 0)) {
                row.error(errors, COL_SCORE, "Điểm phải từ 0 đến " + MAX_SCORE.toPlainString());
            }
            String review = row.text(COL_REVIEW);
            if (review != null && review.length() > 2000) {
                row.error(errors, COL_REVIEW, "Nhận xét tối đa 2000 ký tự");
            }
            if (errorCount(errors) == before && (leader != null || newLeader != null) && courseResult != null) {
                result.add(new Parsed(row, leader, newLeader, courseResult, score, review));
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
