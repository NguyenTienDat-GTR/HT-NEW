package phucuong.ht.modules.training.registrationTemplate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import phucuong.ht.common.excel.ExcelExportSupport;
import phucuong.ht.common.excel.ExcelLabels;
import phucuong.ht.common.excel.job.ExcelExportFile;
import phucuong.ht.common.excel.job.ExcelExportHandler;
import phucuong.ht.common.excel.job.ExcelExportRequest;
import phucuong.ht.common.excel.job.ExcelJobKind;
import phucuong.ht.common.excel.job.JobProgress;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.exception.errorCode;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.training.courseParticipation.model.response.CourseParticipationResponse;
import phucuong.ht.modules.training.courseParticipation.service.CourseParticipationService;
import phucuong.ht.modules.training.courseParticipation.service.ParticipationEligibilityChecker;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;

import java.text.Collator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Xuất danh sách đăng ký của một khóa theo mẫu Excel của giáo phận tổ chức khóa.
 * Tùy chọn: groupBy = NONE | DEANERY | PARISH; layout = SINGLE_SHEET | SHEET_PER_GROUP | FILE_PER_GROUP (zip).
 */
@Component
@RequiredArgsConstructor
public class RegistrationTemplateExportHandler implements ExcelExportHandler {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale VI = new Locale("vi", "VN");

    private final RegistrationTemplateService templateService;
    private final CourseParticipationService participationService;
    private final ParticipationEligibilityChecker eligibilityChecker;
    private final LeaderLevelService leaderLevelService;
    private final jakarta.persistence.EntityManager entityManager;
    private final ParishRepository parishRepository;
    private final DeaneryRepository deaneryRepository;
    private final TransactionTemplate transactionTemplate;

    public enum GroupBy { NONE, DEANERY, PARISH }

    @Override
    public ExcelJobKind kind() {
        return ExcelJobKind.EXPORT_REGISTRATION_TEMPLATE;
    }

    @Override
    public String fileBase(ExcelExportRequest request) {
        Object code = request == null || request.search().getFilters() == null ? null : request.search().getFilters().get("course.courseCode");
        return kind().fileBase() + (code == null ? "" : "-" + RegistrationTemplateRenderer.slug(String.valueOf(code)));
    }

    @Override
    public ExcelExportFile export(ExcelExportRequest request, Authentication authentication, JobProgress progress) {
        GroupBy groupBy = groupBy(request.option("groupBy"));
        RegistrationTemplateRenderer.Layout layout = layout(request.option("layout"));
        if (groupBy == GroupBy.NONE) {
            layout = RegistrationTemplateRenderer.Layout.SINGLE_SHEET;
        }
        Object courseCode = request.search().getFilters() == null ? null : request.search().getFilters().get("course.courseCode");
        RegistrationTemplateService.ExportTemplate template = templateService.forCourse(courseCode == null ? null : String.valueOf(courseCode));
        if (!RegistrationTemplateRenderer.hasDataRow(template.content())) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR,
                    "Mẫu danh sách đăng ký chưa có dòng dữ liệu. Trong mẫu, kéo các trường như Họ và tên, Ngày sinh vào cùng một dòng của bảng");
        }

        List<CourseParticipationResponse> rows = ExcelExportSupport.collectAll(
                request.search(), page -> participationService.findAll(page, authentication), progress);
        Map<String, String> levelNames = leaderLevelService.levelNamesFor(authentication);
        Context context = Objects.requireNonNull(transactionTemplate.execute(status -> load(rows)));

        List<RegistrationTemplateRenderer.Group> groups = groups(rows, groupBy, context, levelNames);
        Map<String, String> common = courseValues(template.course(), levelNames);
        String prefix = "danh-sach-dang-ky-" + RegistrationTemplateRenderer.slug(template.course().getCourseName());
        if (layout == RegistrationTemplateRenderer.Layout.FILE_PER_GROUP) {
            return ExcelExportFile.zip(RegistrationTemplateRenderer.renderZip(template.content(), common, groups, prefix));
        }
        return ExcelExportFile.xlsx(RegistrationTemplateRenderer.renderWorkbook(template.content(), common, groups,
                layout, groupBy != GroupBy.NONE));
    }

    // ---- dữ liệu ----

    private record Context(Map<UUID, Leader> leaders, Map<UUID, Map<String, Integer>> achievedYears,
                           Map<UUID, Parish> parishes, Map<UUID, Deanery> deaneries) {
    }

    private Context load(List<CourseParticipationResponse> rows) {
        Set<UUID> leaderIds = rows.stream().map(CourseParticipationResponse::getLeaderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Leader> leaders = new HashMap<>();
        List<UUID> ids = new ArrayList<>(leaderIds);
        for (int from = 0; from < ids.size(); from += 1000) {
            entityManager.createQuery("select l from Leader l where l.id in :ids", Leader.class)
                    .setParameter("ids", ids.subList(from, Math.min(from + 1000, ids.size())))
                    .getResultList()
                    .forEach(leader -> leaders.put(leader.getId(), leader));
        }
        Map<UUID, Parish> parishes = new HashMap<>();
        rows.stream().map(CourseParticipationResponse::getParishId).filter(Objects::nonNull).distinct()
                .forEach(id -> parishRepository.findById(id).ifPresent(parish -> {
                    parish.getName();
                    if (parish.getParentParish() != null) {
                        parish.getParentParish().getName();
                    }
                    parishes.put(id, parish);
                }));
        Map<UUID, Deanery> deaneries = new HashMap<>();
        rows.stream().map(CourseParticipationResponse::getDeaneryId).filter(Objects::nonNull).distinct()
                .forEach(id -> deaneryRepository.findById(id).ifPresent(deanery -> {
                    deanery.getName();
                    deaneries.put(id, deanery);
                }));
        return new Context(leaders, leaderIds.isEmpty() ? Map.of() : eligibilityChecker.achievedYears(leaderIds), parishes, deaneries);
    }

    private List<RegistrationTemplateRenderer.Group> groups(List<CourseParticipationResponse> rows, GroupBy groupBy, Context context,
                                                            Map<String, String> levelNames) {
        if (groupBy == GroupBy.NONE) {
            Map<String, String> values = new HashMap<>();
            // Cả danh sách thuộc cùng một giáo xứ / giáo hạt thì vẫn điền được tên đơn vị lên phần đầu.
            Set<UUID> parishIds = rows.stream().map(CourseParticipationResponse::getParishId).collect(Collectors.toSet());
            Set<UUID> deaneryIds = rows.stream().map(CourseParticipationResponse::getDeaneryId).collect(Collectors.toSet());
            if (parishIds.size() == 1 && parishIds.iterator().next() != null) {
                values.putAll(parishValues(context.parishes().get(parishIds.iterator().next())));
            }
            if (deaneryIds.size() == 1 && deaneryIds.iterator().next() != null) {
                values.putAll(deaneryValues(context.deaneries().get(deaneryIds.iterator().next())));
            }
            fillGroupDefaults(values, values.getOrDefault("tenGiaoXu", values.getOrDefault("tenGiaoHat", "")), rows.size());
            return List.of(new RegistrationTemplateRenderer.Group("Danh sách", values, rowValues(rows, context, levelNames)));
        }
        Map<UUID, List<CourseParticipationResponse>> byKey = new LinkedHashMap<>();
        for (CourseParticipationResponse row : rows) {
            byKey.computeIfAbsent(groupBy == GroupBy.PARISH ? row.getParishId() : row.getDeaneryId(), key -> new ArrayList<>()).add(row);
        }
        List<RegistrationTemplateRenderer.Group> groups = new ArrayList<>();
        for (Map.Entry<UUID, List<CourseParticipationResponse>> entry : byKey.entrySet()) {
            List<CourseParticipationResponse> members = entry.getValue();
            CourseParticipationResponse first = members.get(0);
            Map<String, String> values = new HashMap<>();
            String name;
            if (groupBy == GroupBy.PARISH) {
                Parish parish = entry.getKey() == null ? null : context.parishes().get(entry.getKey());
                values.putAll(parishValues(parish));
                values.putAll(deaneryValues(first.getDeaneryId() == null ? null : context.deaneries().get(first.getDeaneryId())));
                name = parish == null ? (first.getParishName() == null ? "Chưa có giáo xứ" : first.getParishName()) : unitLabel(parish);
            } else {
                Deanery deanery = entry.getKey() == null ? null : context.deaneries().get(entry.getKey());
                values.putAll(deaneryValues(deanery));
                name = deanery == null ? (first.getDeaneryName() == null ? "Chưa có giáo hạt" : first.getDeaneryName()) : "Giáo hạt " + deanery.getName();
            }
            fillGroupDefaults(values, name, members.size());
            groups.add(new RegistrationTemplateRenderer.Group(name, values, rowValues(members, context, levelNames)));
        }
        Collator collator = Collator.getInstance(VI);
        groups.sort((a, b) -> collator.compare(a.name(), b.name()));
        return groups;
    }

    private static void fillGroupDefaults(Map<String, String> values, String groupName, int count) {
        for (String key : List.of("tenGiaoHat", "tenHiepDoan", "tenGiaoXu", "tenXuDoan", "chaTuyenUy", "giaoHatVaTen", "giaoXuVaTen", "giaoHoDiemVaTen", "loaiGiaoHoDiem", "tenGiaoHoDiem")) {
            values.putIfAbsent(key, "");
        }
        values.put("tenNhom", groupName == null ? "" : groupName);
        values.put("soNguoi", String.valueOf(count));
    }

    private static List<Map<String, Object>> rowValues(List<CourseParticipationResponse> rows, Context context, Map<String, String> levelNames) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (CourseParticipationResponse row : rows) {
            Leader leader = row.getLeaderId() == null ? null : context.leaders().get(row.getLeaderId());
            Map<String, Object> values = new HashMap<>();
            String holyName = blankToNull(row.getLeaderHolyName());
            values.put("tenThanh", holyName);
            values.put("hoVaTen", row.getLeaderFullName());
            values.put("tenThanhHoVaTen", holyName == null ? row.getLeaderFullName() : holyName + " " + nullToEmpty(row.getLeaderFullName()));
            LocalDate birthDate = leader == null ? null : leader.getBirthDate();
            values.put("ngaySinh", birthDate == null ? null : birthDate.format(DATE));
            values.put("namSinh", birthDate == null ? null : birthDate.getYear());
            values.put("gioiTinh", leader == null ? null : blankToNull(ExcelLabels.gender(leader.getGender())));
            values.put("soDienThoai", row.getLeaderPhone());
            values.put("email", row.getLeaderEmail());
            // Đơn vị của hồ sơ là giáo họ / giáo điểm thì giáo xứ là giáo xứ mẹ; giáo họ / giáo điểm chỉ in khi hồ sơ thuộc loại đó.
            Parish unit = row.getParishId() == null ? null : context.parishes().get(row.getParishId());
            boolean subUnit = isSubUnit(unit);
            values.put("giaoXu", unit == null ? row.getParishName() : mainParish(unit).getName());
            values.put("loaiHoDiem", subUnit ? unit.getUnitType().getLabel() : null);
            values.put("tenHoDiem", subUnit ? unit.getName() : null);
            values.put("giaoHoDiem", subUnit ? unit.getUnitType().getLabel() + " " + unit.getName() : null);
            values.put("giaoHat", row.getDeaneryName());
            String level = row.getLeaderLevel();
            values.put("capHienTai", level == null ? null : levelNames.getOrDefault(level, level));
            values.put("namDatCap", level == null || row.getLeaderId() == null ? null
                    : context.achievedYears().getOrDefault(row.getLeaderId(), Map.of()).get(level));
            values.put("ngayDangKy", row.getRegistrationDate() == null ? null : row.getRegistrationDate().format(DATE));
            values.put("trangThaiDuyet", blankToNull(ExcelLabels.approval(row.getApprovalStatus())));
            values.put("doi", row.getTeamName());
            values.put("ghiChu", row.getRegistrationNote());
            result.add(values);
        }
        return result;
    }

    private static Map<String, String> courseValues(TrainingCourse course, Map<String, String> levelNames) {
        Map<String, String> values = new HashMap<>();
        Diocese diocese = course.getDiocese();
        values.put("tenGiaoPhan", diocese == null ? "" : nullToEmpty(diocese.getName()));
        values.put("giaoPhanVaTen", diocese == null ? "Giáo phận" : "Giáo phận " + nullToEmpty(diocese.getName()));
        values.put("tenLienDoan", diocese == null ? "" : nullToEmpty(diocese.getUnionName()));
        // Cấp đơn vị theo cấp quản lý của khóa: khóa cấp giáo phận -> liên đoàn, cấp giáo hạt -> hiệp đoàn.
        boolean deaneryLevel = course.getDeanery() != null;
        String levelLabel = deaneryLevel ? "Hiệp đoàn" : "Liên đoàn";
        String levelName = deaneryLevel ? nullToEmpty(course.getDeanery().getAssociationName()) : diocese == null ? "" : nullToEmpty(diocese.getUnionName());
        values.put("capDonVi", levelLabel);
        // Cấp giáo hội theo cấp quản lý của khóa: khóa cấp giáo phận -> Giáo phận, cấp giáo hạt -> Giáo hạt.
        String churchLabel = deaneryLevel ? "Giáo hạt" : "Giáo phận";
        String churchName = deaneryLevel ? nullToEmpty(course.getDeanery().getName()) : diocese == null ? "" : nullToEmpty(diocese.getName());
        values.put("capGiaoHoi", churchLabel);
        values.put("tenGiaoHoi", churchName);
        values.put("capVaTenGiaoHoi", churchName.isBlank() ? churchLabel : churchLabel + " " + churchName);
        values.put("tenDonVi", levelName);
        values.put("capVaTenDonVi", levelName.isBlank() ? levelLabel : levelLabel + " " + levelName);
        values.put("tenKhoa", nullToEmpty(course.getCourseName()));
        // Cấp độ huynh trưởng cần có để đăng ký khóa (theo loại khóa); trống nếu khóa không yêu cầu cấp độ.
        String requiredLevel = course.getCourseType() == null ? null : course.getCourseType().getRequiredCurrentLevel();
        values.put("capDoYeuCau", requiredLevel == null || requiredLevel.isBlank() ? "" : levelNames.getOrDefault(requiredLevel, requiredLevel));
        values.put("maKhoa", nullToEmpty(course.getCourseCode()));
        values.put("loaiKhoa", course.getCourseType() == null ? "" : nullToEmpty(course.getCourseType().getTypeName()));
        values.put("namToChuc", course.getHostYear() == null ? "" : String.valueOf(course.getHostYear()));
        values.put("diaDiem", nullToEmpty(course.getLocation()));
        values.put("ngayBatDau", course.getStartDate() == null ? "" : course.getStartDate().format(DATE));
        values.put("ngayKetThuc", course.getEndDate() == null ? "" : course.getEndDate().format(DATE));
        values.put("donViToChuc", course.getDeanery() != null ? "Giáo hạt " + course.getDeanery().getName()
                : diocese == null ? "" : "Giáo phận " + diocese.getName());
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        values.put("ngayXuat", today.format(DATE));
        values.put("ngay", String.valueOf(today.getDayOfMonth()));
        values.put("thang", String.valueOf(today.getMonthValue()));
        values.put("nam", String.valueOf(today.getYear()));
        return values;
    }

    private static boolean isSubUnit(Parish parish) {
        return parish != null && parish.getUnitType() != null && parish.getUnitType() != phucuong.ht.modules.parish.model.enums.ParishUnitType.PARISH;
    }

    private static Parish mainParish(Parish parish) {
        return isSubUnit(parish) && parish.getParentParish() != null ? parish.getParentParish() : parish;
    }

    private static Map<String, String> parishValues(Parish parish) {
        Map<String, String> values = new HashMap<>();
        if (parish == null) {
            return values;
        }
        // Giáo xứ luôn là giáo xứ thật (giáo xứ mẹ nếu đơn vị là giáo họ / giáo điểm); giáo họ / giáo điểm có trường riêng và để trống nếu không có.
        Parish main = mainParish(parish);
        boolean subUnit = isSubUnit(parish);
        values.put("tenGiaoXu", main.getName());
        values.put("giaoXuVaTen", "Giáo xứ " + main.getName());
        values.put("loaiGiaoHoDiem", subUnit ? parish.getUnitType().getLabel() : "");
        values.put("tenGiaoHoDiem", subUnit ? parish.getName() : "");
        values.put("giaoHoDiemVaTen", subUnit ? parish.getUnitType().getLabel() + " " + parish.getName() : "");
        values.put("tenXuDoan", nullToEmpty(parish.getChapterName()));
        values.put("chaTuyenUy", nullToEmpty(parish.getChaplain()));
        return values;
    }

    private static Map<String, String> deaneryValues(Deanery deanery) {
        Map<String, String> values = new HashMap<>();
        if (deanery == null) {
            return values;
        }
        values.put("tenGiaoHat", deanery.getName());
        values.put("giaoHatVaTen", "Giáo hạt " + deanery.getName());
        values.put("tenHiepDoan", nullToEmpty(deanery.getAssociationName()));
        return values;
    }

    private static String unitLabel(Parish parish) {
        String label = parish.getUnitType() == null ? "Giáo xứ" : parish.getUnitType().getLabel();
        return label + " " + parish.getName();
    }

    static GroupBy groupBy(String value) {
        if (value == null) {
            return GroupBy.NONE;
        }
        try {
            return GroupBy.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Cách gom nhóm không hợp lệ");
        }
    }

    static RegistrationTemplateRenderer.Layout layout(String value) {
        if (value == null) {
            return RegistrationTemplateRenderer.Layout.SINGLE_SHEET;
        }
        try {
            return RegistrationTemplateRenderer.Layout.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, errorCode.VALIDATION_ERROR, "Cách chia sheet / file không hợp lệ");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
