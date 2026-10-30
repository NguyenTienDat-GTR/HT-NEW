package phucuong.ht.modules.leader.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.excel.ExcelRowData;
import phucuong.ht.common.excel.ExcelSheetData;
import phucuong.ht.common.excel.ExcelWorkbookReader;
import phucuong.ht.common.excel.AvatarImportCollector;
import phucuong.ht.common.excel.AvatarPlan;
import phucuong.ht.common.excel.AvatarWarning;
import phucuong.ht.common.excel.AvatarZip;
import phucuong.ht.common.excel.ImportErrors;
import phucuong.ht.common.excel.ImportSummary;
import phucuong.ht.common.excel.job.JobProgress;
import phucuong.ht.common.security.UnitOperationalStatusService;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.CacheNames;
import phucuong.ht.config.EvictCaches;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.modules.leader.helper.LeaderScopeHelper;
import phucuong.ht.modules.leader.helper.LeaderValidationHelper;
import phucuong.ht.modules.leader.model.entity.Leader;
import phucuong.ht.modules.leader.model.enums.LeaderGender;
import phucuong.ht.modules.leader.model.request.LeaderCreateRequest;
import phucuong.ht.modules.leader.repository.LeaderRepository;
import phucuong.ht.modules.leaderLevel.service.LeaderLevelService;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishRepository;

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
import java.util.regex.Pattern;

/**
 * Import hồ sơ huynh trưởng từ Excel. Kiểm tra toàn bộ file trước; có bất kỳ dòng lỗi nào thì không lưu gì.
 * Dùng lại quy tắc phạm vi / giáo xứ đang hoạt động / tách họ tên / danh mục cấp độ của chức năng tạo huynh trưởng.
 */
@Service
@RequiredArgsConstructor
public class LeaderImportService {

    public static final String COL_HOLY_NAME = "Tên thánh";
    public static final String COL_FULL_NAME = "Họ và tên";
    public static final String COL_BIRTH_DATE = "Ngày sinh";
    public static final String COL_GENDER = "Giới tính";
    public static final String COL_EMAIL = "Email";
    public static final String COL_PHONE = "Số điện thoại";
    public static final String COL_LEVEL = "Cấp độ";
    public static final String COL_DEANERY = "Giáo hạt";
    public static final String COL_PARISH = "Giáo xứ";
    public static final String COL_AVATAR_FILE = "Tên file ảnh";

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^0\\d{9}$");

    private final LeaderRepository leaderRepository;
    private final ParishRepository parishRepository;
    private final LeaderScopeHelper leaderScopeHelper;
    private final LeaderValidationHelper leaderValidationHelper;
    private final LeaderLevelService leaderLevelService;
    private final CommonValidationHelper commonValidationHelper;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private UnitOperationalStatusService unitOperationalStatusService;

    private record Row(ExcelRowData source, String holyName, String fullName, LocalDate birthDate, LeaderGender gender,
                       String email, String phone, String level, Parish parish, String avatarFileName) {
    }

    @EvictCaches.Analytics
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_DETAIL, allEntries = true)
    })
    @Transactional
    public ImportSummary importLeaders(java.nio.file.Path file, Authentication authentication, JobProgress progress) {
        return importLeaders(file, authentication, progress, null);
    }

    public ImportSummary importLeaders(java.nio.file.Path file, Authentication authentication, JobProgress progress, AvatarZip images) {
        String actor = leaderValidationHelper.getRequiredAuditActor(authentication);
        List<Row> rows;
        try (ExcelWorkbookReader reader = ExcelWorkbookReader.open(file)) {
            ExcelSheetData sheet = reader.firstSheet();
            ImportErrors errors = new ImportErrors();
            sheet.requireColumns(errors, COL_HOLY_NAME, COL_FULL_NAME, COL_BIRTH_DATE, COL_GENDER, COL_EMAIL, COL_PHONE, COL_DEANERY, COL_PARISH);
            errors.throwIfAny();
            if (sheet.rows().isEmpty()) {
                throw commonValidationHelper.validationError("File Excel không có dòng dữ liệu nào");
            }
            rows = parse(sheet, authentication, errors);
            errors.throwIfAny();
        }
        progress.report(0, rows.size());

        OffsetDateTime now = OffsetDateTime.now();
        List<AvatarPlan> avatarPlan = new ArrayList<>();
        List<AvatarWarning> avatarWarnings = new ArrayList<>();
        int written = 0;
        for (Row item : rows) {
            Leader leader = leaderRepository.save(buildLeader(new NewLeader(item.holyName(), item.fullName(), item.birthDate(), item.gender(), item.email(),
                    item.phone(), item.level(), item.parish()), actor, now));
            AvatarImportCollector.collect(images, avatarPlan, avatarWarnings, item.source().number(), leader.getId(), item.fullName(),
                    item.avatarFileName(), item.parish().getDeanery().getDiocese().getId());
            written++;
            if (written % 200 == 0) {
                progress.report(written, rows.size());
            }
        }
        return new ImportSummary(rows.size(), "Đã import " + rows.size() + " huynh trưởng", avatarPlan, avatarWarnings);
    }

    private List<Row> parse(ExcelSheetData sheet, Authentication authentication, ImportErrors errors) {
        ParishIndex parishesByName = indexParishes(parishRepository.findAllWithDeaneryAndDiocese());

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
        Set<String> existingEmails = new HashSet<>();
        for (Leader leader : leaderRepository.findAllByEmailsIgnoreCase(emails)) {
            existingEmails.add(leader.getEmail().toLowerCase(Locale.ROOT));
        }
        Set<String> existingPhones = new HashSet<>();
        for (Leader leader : leaderRepository.findAllByPhoneNumbers(phones)) {
            existingPhones.add(leader.getPhoneNumber());
        }

        Map<UUID, String> parishProblems = new HashMap<>();
        Map<UUID, LeaderLevelService.LevelOrder> levelsByDiocese = new HashMap<>();
        Set<String> seenEmails = new HashSet<>();
        Set<String> seenPhones = new HashSet<>();
        List<Row> result = new ArrayList<>();

        for (ExcelRowData row : sheet.rows()) {
            int before = errors.count();
            String holyName = row.requiredText(errors, COL_HOLY_NAME);
            if (holyName != null && holyName.length() > 50) {
                row.error(errors, COL_HOLY_NAME, "Tên thánh tối đa 50 ký tự");
            }
            String fullName = row.requiredText(errors, COL_FULL_NAME);
            if (fullName != null && fullName.length() > 200) {
                row.error(errors, COL_FULL_NAME, "Họ và tên tối đa 200 ký tự");
            }
            LocalDate birthDate = row.date(errors, COL_BIRTH_DATE, true);
            if (birthDate != null && birthDate.isAfter(LocalDate.now())) {
                row.error(errors, COL_BIRTH_DATE, "Ngày sinh không được sau ngày hiện tại");
            }
            LeaderGender gender = parseGender(row, errors);
            String avatarFileName = row.text(COL_AVATAR_FILE);
            if (avatarFileName != null && avatarFileName.length() > 255) {
                row.error(errors, COL_AVATAR_FILE, "Tên file ảnh tối đa 255 ký tự");
            }

            String email = row.requiredText(errors, COL_EMAIL);
            if (email != null) {
                if (email.length() > 100 || !EMAIL.matcher(email).matches()) {
                    row.error(errors, COL_EMAIL, "Email không hợp lệ");
                } else {
                    String key = email.toLowerCase(Locale.ROOT);
                    if (existingEmails.contains(key)) {
                        row.error(errors, COL_EMAIL, "Email đã tồn tại trong hệ thống");
                    } else if (!seenEmails.add(key)) {
                        row.error(errors, COL_EMAIL, "Email bị trùng với dòng khác trong file");
                    }
                }
            }
            String phone = row.phone(COL_PHONE);
            if (phone == null) {
                row.error(errors, COL_PHONE, "Bắt buộc nhập");
            } else {
                if (!PHONE.matcher(phone).matches()) {
                    row.error(errors, COL_PHONE, "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0");
                } else if (existingPhones.contains(phone)) {
                    row.error(errors, COL_PHONE, "Số điện thoại đã tồn tại trong hệ thống");
                } else if (!seenPhones.add(phone)) {
                    row.error(errors, COL_PHONE, "Số điện thoại bị trùng với dòng khác trong file");
                }
            }

            Parish parish = resolveParish(row, parishesByName,
                    candidate -> parishProblems.computeIfAbsent(candidate.getId(), id -> checkParish(candidate, authentication)), errors);
            String level = null;
            if (parish != null) {
                UUID dioceseId = parish.getDeanery().getDiocese().getId();
                LeaderLevelService.LevelOrder order = levelsByDiocese.computeIfAbsent(dioceseId, leaderLevelService::order);
                level = resolveLevel(row, order, errors);
            }

            if (errors.count() == before && parish != null) {
                result.add(new Row(row, holyName, fullName, birthDate, gender, email, phone, level, parish, avatarFileName));
            }
        }
        return result;
    }

    // ---- Dùng chung với import danh sách tham dự khóa quá khứ (tạo huynh trưởng chưa có hồ sơ ngay trong file danh sách tham dự) ----

    /** Thông tin một huynh trưởng mới đọc từ một dòng Excel. */
    public record NewLeader(String holyName, String fullName, LocalDate birthDate, LeaderGender gender, String email, String phone,
                            String level, Parish parish) {
    }

    /** Chỉ mục giáo xứ theo tên và tập tên giáo hạt (không dấu, không phân biệt hoa thường) để đối chiếu hai cột Giáo hạt, Giáo xứ với dữ liệu hệ thống. */
    public record ParishIndex(Map<String, List<Parish>> byName, Set<String> deaneryNames, Map<String, List<Parish>> byDeaneryAndName) {
    }

    public ParishIndex indexParishes(java.util.Collection<Parish> parishes) {
        Map<String, List<Parish>> byName = new HashMap<>();
        Set<String> deaneryNames = new HashSet<>();
        // Khóa "giáo hạt|giáo xứ" đã chuẩn hóa: mỗi dòng Excel tra thẳng một lần, không duyệt lại danh sách giáo xứ cùng tên.
        Map<String, List<Parish>> byDeaneryAndName = new HashMap<>();
        for (Parish parish : parishes) {
            String parishKey = ExcelWorkbookReader.normalize(parish.getName());
            byName.computeIfAbsent(parishKey, key -> new ArrayList<>()).add(parish);
            if (parish.getDeanery() != null) {
                String deaneryKey = ExcelWorkbookReader.normalize(parish.getDeanery().getName());
                deaneryNames.add(deaneryKey);
                byDeaneryAndName.computeIfAbsent(deaneryKey + "|" + parishKey, key -> new ArrayList<>()).add(parish);
            }
        }
        return new ParishIndex(byName, deaneryNames, byDeaneryAndName);
    }

    /**
     * Đọc và kiểm tra thông tin huynh trưởng MỚI của một dòng (bắt buộc: Tên thánh, Họ và tên, Ngày sinh, Giới tính, Email, Giáo xứ;
     * tuỳ chọn: Số điện thoại, Cấp độ, Giáo hạt). Không kiểm tra trùng với huynh trưởng khác - người gọi đã xác định huynh trưởng này chưa có.
     * {@code problemOf} trả chuỗi rỗng nếu giáo xứ hợp lệ, ngược lại là lý do giáo xứ không dùng được. Trả null và ghi lỗi nếu dòng sai.
     */
    public NewLeader parseNewLeader(ExcelRowData row, ImportErrors errors, ParishIndex parishIndex,
                                    java.util.function.Function<Parish, String> problemOf, Map<UUID, LeaderLevelService.LevelOrder> levelCache) {
        int before = errors.count();
        String holyName = row.requiredText(errors, COL_HOLY_NAME);
        if (holyName != null && holyName.length() > 50) {
            row.error(errors, COL_HOLY_NAME, "Tên thánh tối đa 50 ký tự");
        }
        String fullName = row.requiredText(errors, COL_FULL_NAME);
        if (fullName != null && fullName.length() > 200) {
            row.error(errors, COL_FULL_NAME, "Họ và tên tối đa 200 ký tự");
        }
        LocalDate birthDate = row.date(errors, COL_BIRTH_DATE, true);
        if (birthDate != null && birthDate.isAfter(LocalDate.now())) {
            row.error(errors, COL_BIRTH_DATE, "Ngày sinh không được sau ngày hiện tại");
        }
        LeaderGender gender = parseGender(row, errors);
        String email = row.requiredText(errors, COL_EMAIL);
        if (email != null && (email.length() > 100 || !EMAIL.matcher(email).matches())) {
            row.error(errors, COL_EMAIL, "Email không hợp lệ");
        }
        String phone = row.phone(COL_PHONE);
        if (phone == null) {
            row.error(errors, COL_PHONE, "Bắt buộc nhập");
        } else if (!PHONE.matcher(phone).matches()) {
            row.error(errors, COL_PHONE, "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0");
        }
        Parish parish = resolveParish(row, parishIndex, problemOf, errors);
        String level = null;
        if (parish != null) {
            LeaderLevelService.LevelOrder order = levelCache.computeIfAbsent(parish.getDeanery().getDiocese().getId(), leaderLevelService::order);
            level = resolveLevel(row, order, errors);
        }
        if (errors.count() != before || parish == null) {
            return null;
        }
        return new NewLeader(holyName, fullName, birthDate, gender, email, phone, level, parish);
    }

    /** Dựng entity huynh trưởng (chưa lưu) từ thông tin đã kiểm tra, tách họ tên như khi tạo thủ công. */
    public Leader buildLeader(NewLeader data, String actor, OffsetDateTime now) {
        LeaderValidationHelper.LeaderNameParts parts = leaderValidationHelper.splitFullName(data.fullName());
        Leader leader = new Leader();
        leader.setHolyName(data.holyName());
        leader.setFirstName(parts.firstName());
        leader.setLastName(parts.lastName());
        leader.setBirthDate(data.birthDate());
        leader.setEmail(data.email());
        leader.setPhoneNumber(data.phone());
        leader.setGender(data.gender());
        leader.setLeaderLevel(data.level());
        leader.setParish(data.parish());
        leader.setStatus(Boolean.TRUE);
        leader.setCreatedAt(now);
        leader.setUpdatedAt(now);
        leader.setCreatedBy(actor);
        leader.setUpdatedBy(actor);
        return leader;
    }

    private LeaderGender parseGender(ExcelRowData row, ImportErrors errors) {
        String text = row.requiredText(errors, COL_GENDER);
        if (text == null) {
            return null;
        }
        return switch (ExcelWorkbookReader.normalize(text)) {
            case "nam", "male" -> LeaderGender.NAM;
            case "nu", "female" -> LeaderGender.NU;
            default -> {
                row.error(errors, COL_GENDER, "Giới tính phải là \"Nam\" hoặc \"Nữ\"");
                yield null;
            }
        };
    }

    // Cấp độ nhập theo tên hoặc mã trong danh mục của giáo phận; để trống là "chưa có cấp".
    private String resolveLevel(ExcelRowData row, LeaderLevelService.LevelOrder order, ImportErrors errors) {
        String text = row.text(COL_LEVEL);
        if (text == null) {
            return LeaderLevelService.NONE;
        }
        String wanted = ExcelWorkbookReader.normalize(text);
        for (Map.Entry<String, String> entry : order.names().entrySet()) {
            if (ExcelWorkbookReader.normalize(entry.getKey()).equals(wanted) || ExcelWorkbookReader.normalize(entry.getValue()).equals(wanted)) {
                return entry.getKey();
            }
        }
        row.error(errors, COL_LEVEL, "Cấp độ \"" + text + "\" không có trong danh mục cấp độ của giáo phận");
        return null;
    }

    // Giáo hạt và Giáo xứ đều bắt buộc và phải CÓ SẴN trong hệ thống (không tự tạo mới), giáo xứ phải thuộc đúng giáo hạt đã nhập.
    private Parish resolveParish(ExcelRowData row, ParishIndex parishIndex,
                                 java.util.function.Function<Parish, String> problemOf, ImportErrors errors) {
        String deaneryName = row.requiredText(errors, COL_DEANERY);
        String parishName = row.requiredText(errors, COL_PARISH);
        if (parishName == null || deaneryName == null) {
            return null;
        }
        String wantedDeanery = ExcelWorkbookReader.normalize(deaneryName);
        List<Parish> sameName = parishIndex.byName().getOrDefault(ExcelWorkbookReader.normalize(parishName), List.of());
        boolean deaneryKnown = parishIndex.deaneryNames().contains(wantedDeanery);
        if (!deaneryKnown) {
            row.error(errors, COL_DEANERY, "Giáo hạt '" + deaneryName + "' chưa có trong hệ thống");
        }
        if (sameName.isEmpty()) {
            row.error(errors, COL_PARISH, "Giáo xứ '" + parishName + "' chưa có trong hệ thống");
        }
        if (!deaneryKnown || sameName.isEmpty()) {
            return null;
        }
        List<Parish> matches = parishIndex.byDeaneryAndName().getOrDefault(wantedDeanery + "|" + ExcelWorkbookReader.normalize(parishName), List.of());
        if (matches.isEmpty()) {
            row.error(errors, COL_PARISH, "Giáo xứ '" + parishName + "' không thuộc giáo hạt '" + deaneryName + "'");
            return null;
        }
        // Chỉ giữ giáo xứ mà tài khoản hiện tại được phép tạo huynh trưởng (cùng quy tắc phạm vi khi tạo thủ công).
        List<Parish> allowed = new ArrayList<>();
        String denied = null;
        for (Parish parish : matches) {
            String problem = problemOf.apply(parish);
            if (problem.isEmpty()) {
                allowed.add(parish);
            } else {
                denied = problem;
            }
        }
        if (allowed.size() == 1) {
            return allowed.get(0);
        }
        if (allowed.size() > 1) {
            row.error(errors, COL_PARISH, "Có nhiều giáo xứ '" + parishName + "' thuộc giáo hạt trùng tên '" + deaneryName + "', không xác định được giáo xứ nào");
        } else {
            row.error(errors, COL_PARISH, denied != null ? denied : "Giáo xứ '" + parishName + "' không dùng được");
        }
        return null;
    }

    // Chuỗi rỗng nghĩa là hợp lệ; ngược lại là lý do không tạo được huynh trưởng trong giáo xứ này.
    private String checkParish(Parish parish, Authentication authentication) {
        try {
            LeaderCreateRequest probe = new LeaderCreateRequest();
            probe.setParishId(parish.getId());
            leaderScopeHelper.enforceCreateScope(probe, authentication);
            if (unitOperationalStatusService != null) {
                unitOperationalStatusService.requireUnitOperational(UnitType.PARISH, parish.getId());
            }
            return "";
        } catch (ApiException exception) {
            return exception.getMessage() == null ? "Không thể tạo huynh trưởng trong giáo xứ này" : exception.getMessage();
        }
    }
}
