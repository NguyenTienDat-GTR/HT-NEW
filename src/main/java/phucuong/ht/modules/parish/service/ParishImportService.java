package phucuong.ht.modules.parish.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.enums.UnitType;
import phucuong.ht.common.excel.AbstractExcelImporter;
import phucuong.ht.common.excel.ExcelRowData;
import phucuong.ht.common.excel.ExcelSheetData;
import phucuong.ht.common.excel.ExcelWorkbookReader;
import phucuong.ht.common.excel.ImportErrors;
import phucuong.ht.common.excel.ImportSummary;
import phucuong.ht.common.excel.job.JobProgress;
import phucuong.ht.common.exception.ApiException;
import phucuong.ht.common.security.UnitOperationalStatusService;
import phucuong.ht.common.validation.CommonValidationHelper;
import phucuong.ht.config.CacheNames;
import phucuong.ht.config.EvictCaches;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.parish.helper.ParishScopeHelper;
import phucuong.ht.modules.parish.helper.ParishValidationHelper;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.model.enums.ParishUnitType;
import phucuong.ht.modules.parish.model.request.ParishCreateRequest;
import phucuong.ht.modules.parish.repository.ParishRepository;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Import danh sách giáo xứ / giáo họ / giáo điểm từ Excel. Bắt buộc: Tên giáo xứ, Tên giáo hạt (phải đã có trong hệ thống).
 * Cột Loại để trống là giáo xứ; giáo họ / giáo điểm phải điền Loại và Giáo xứ cha (cùng giáo hạt, có thể là giáo xứ mới trong cùng file).
 * Kiểm tra toàn bộ file trước; có bất kỳ dòng lỗi nào thì không lưu gì.
 */
@Service
@RequiredArgsConstructor
public class ParishImportService extends AbstractExcelImporter<ParishImportService.Row, ParishImportService.ImportRun> {

    public static final String COL_NAME = "Tên giáo xứ";
    public static final String COL_TYPE = "Loại";
    public static final String COL_DEANERY = "Tên giáo hạt";
    public static final String COL_PARENT = "Giáo xứ cha";
    public static final String COL_PATRON_SAINT_DAY = "Ngày bổn mạng";
    public static final String COL_ESTABLISHMENT_DATE = "Ngày thành lập";
    public static final String COL_CHAPLAIN = "Linh mục tuyên úy";
    public static final String COL_PHONE = "Số điện thoại";
    public static final String COL_EMAIL = "Email";
    public static final String COL_CHAPTER = "Tên xứ đoàn";

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^0[1-9]\\d{8}$");

    private final ParishRepository parishRepository;
    private final DeaneryRepository deaneryRepository;
    private final ParishScopeHelper parishScopeHelper;
    private final ParishValidationHelper parishValidationHelper;
    private final CommonValidationHelper commonValidationHelper;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private UnitOperationalStatusService unitOperationalStatusService;

    /** parentKey: khóa "mã giáo hạt|tên giáo xứ cha đã chuẩn hóa" khi giáo xứ cha là giáo xứ mới trong cùng file; ngược lại dùng existingParent. */
    record Row(ExcelRowData source, String name, ParishUnitType unitType, Deanery deanery, Parish existingParent, String parentKey,
               MonthDay patronSaintDay, LocalDate establishmentDate, String chaplain, String phone, String email, String chapter) {
    }

    /** Trạng thái của một lần import (service dùng chung nhiều luồng nên không giữ trong field). */
    static final class ImportRun {
        private final Authentication authentication;
        private final String actor;
        private final Map<String, Parish> created = new HashMap<>();
        private OffsetDateTime now;

        ImportRun(Authentication authentication, String actor) {
            this.authentication = authentication;
            this.actor = actor;
        }
    }

    /** Tên các giáo hạt mà tài khoản được phép tạo giáo xứ (để làm danh sách chọn trong file mẫu). */
    @Transactional(readOnly = true)
    public List<String> selectableDeaneryNames(Authentication authentication) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Map<UUID, String> problems = new HashMap<>();
        for (Deanery deanery : deaneryRepository.findAllWithDiocese()) {
            if (problems.computeIfAbsent(deanery.getId(), id -> checkDeanery(deanery, authentication)).isEmpty()) {
                names.add(deanery.getName());
            }
        }
        return new ArrayList<>(names);
    }

    @EvictCaches.Analytics
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PARISH_DETAIL, allEntries = true)
    })
    @Transactional
    public ImportSummary importParishes(Path file, Authentication authentication, JobProgress progress) {
        String actor = parishValidationHelper.getRequiredAuditActor(authentication);
        return runImport(file, progress, new ImportRun(authentication, actor));
    }

    @Override
    protected List<Row> readRows(ExcelWorkbookReader reader, ImportRun run, ImportErrors errors) {
        ExcelSheetData sheet = reader.firstSheet();
        sheet.requireColumns(errors, COL_NAME, COL_DEANERY);
        errors.throwIfAny();
        if (sheet.rows().isEmpty()) {
            throw commonValidationHelper.validationError("File Excel không có dòng dữ liệu nào");
        }
        boolean hasTypeColumn = sheet.hasColumn(COL_TYPE);
        boolean hasParentColumn = sheet.hasColumn(COL_PARENT);

        Map<String, List<Deanery>> deaneriesByName = new HashMap<>();
        for (Deanery deanery : deaneryRepository.findAllWithDiocese()) {
            deaneriesByName.computeIfAbsent(ExcelWorkbookReader.normalize(deanery.getName()), key -> new ArrayList<>()).add(deanery);
        }
        // Giáo xứ đã có, tra theo "mã giáo hạt|tên": báo trùng tên và tìm giáo xứ cha.
        Map<String, Parish> existing = new HashMap<>();
        for (Parish parish : parishRepository.findAllWithDeaneryAndDiocese()) {
            existing.put(parish.getDeanery().getId() + "|" + ExcelWorkbookReader.normalize(parish.getName()), parish);
        }

        Map<UUID, String> deaneryProblems = new HashMap<>();
        Set<String> seen = new HashSet<>();
        // Giáo xứ (loại PARISH) mới trong file, để giáo họ / giáo điểm trong file có thể chọn làm giáo xứ cha.
        Set<String> parishesInFile = new HashSet<>();
        for (ExcelRowData row : sheet.rows()) {
            String type = row.text(COL_TYPE);
            String name = row.text(COL_NAME);
            String deaneryName = row.text(COL_DEANERY);
            if (name != null && deaneryName != null && (type == null || parseType(type) == ParishUnitType.PARISH)) {
                Deanery deanery = uniqueDeanery(deaneriesByName, deaneryName);
                if (deanery != null) {
                    parishesInFile.add(deanery.getId() + "|" + ExcelWorkbookReader.normalize(name));
                }
            }
        }

        List<Row> result = new ArrayList<>();
        for (ExcelRowData row : sheet.rows()) {
            int before = errors.count();
            String name = row.requiredText(errors, COL_NAME);
            if (name != null && name.length() > 100) {
                row.error(errors, COL_NAME, "Tên giáo xứ tối đa 100 ký tự");
            }
            ParishUnitType unitType = ParishUnitType.PARISH;
            String typeText = hasTypeColumn ? row.text(COL_TYPE) : null;
            if (typeText != null) {
                ParishUnitType parsed = parseType(typeText);
                if (parsed == null) {
                    row.error(errors, COL_TYPE, "Loại chỉ nhận \"Giáo họ\" hoặc \"Giáo điểm\" (để trống nếu là giáo xứ)");
                } else {
                    unitType = parsed;
                }
            }

            Deanery deanery = resolveDeanery(row, deaneriesByName, deaneryProblems, run.authentication, errors);

            Parish existingParent = null;
            String parentKey = null;
            String parentName = hasParentColumn ? row.text(COL_PARENT) : null;
            if (unitType == ParishUnitType.PARISH) {
                if (parentName != null) {
                    row.error(errors, COL_PARENT, "Giáo xứ không thuộc giáo xứ nào khác, hãy để trống cột này");
                }
            } else if (parentName == null) {
                row.error(errors, COL_PARENT, unitType.getLabel() + " phải thuộc một giáo xứ, bắt buộc nhập Giáo xứ cha");
            } else if (deanery != null) {
                String key = deanery.getId() + "|" + ExcelWorkbookReader.normalize(parentName);
                Parish parent = existing.get(key);
                if (parent != null) {
                    if (parent.getUnitType() != ParishUnitType.PARISH) {
                        row.error(errors, COL_PARENT, "Giáo xứ cha phải là giáo xứ, không phải giáo họ hoặc giáo điểm");
                    } else {
                        existingParent = parent;
                    }
                } else if (parishesInFile.contains(key)) {
                    parentKey = key;
                } else {
                    row.error(errors, COL_PARENT, "Giáo xứ cha '" + parentName + "' chưa có trong giáo hạt '" + deaneryName(row) + "'");
                }
            }

            if (name != null && deanery != null && name.length() <= 100) {
                String key = deanery.getId() + "|" + ExcelWorkbookReader.normalize(name);
                if (existing.containsKey(key)) {
                    row.error(errors, COL_NAME, "Tên giáo xứ '" + name + "' đã tồn tại trong giáo hạt '" + deanery.getName() + "'");
                } else if (!seen.add(key)) {
                    row.error(errors, COL_NAME, "Tên giáo xứ bị trùng với dòng khác trong file (cùng giáo hạt)");
                }
            }

            MonthDay patronSaintDay = row.monthDay(errors, COL_PATRON_SAINT_DAY, false);
            LocalDate establishmentDate = row.date(errors, COL_ESTABLISHMENT_DATE, false);
            if (establishmentDate != null && establishmentDate.isAfter(LocalDate.now())) {
                row.error(errors, COL_ESTABLISHMENT_DATE, "Ngày thành lập không được sau ngày hiện tại");
            }
            String chaplain = row.text(COL_CHAPLAIN);
            if (chaplain != null && chaplain.length() > 100) {
                row.error(errors, COL_CHAPLAIN, "Tên linh mục tuyên úy tối đa 100 ký tự");
            }
            String phone = row.phone(COL_PHONE);
            if (phone != null && !PHONE.matcher(phone).matches()) {
                row.error(errors, COL_PHONE, "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0");
            }
            String email = row.text(COL_EMAIL);
            if (email != null && (email.length() > 100 || !EMAIL.matcher(email).matches())) {
                row.error(errors, COL_EMAIL, "Email không hợp lệ");
            }
            String chapter = row.text(COL_CHAPTER);
            if (chapter != null && chapter.length() > 255) {
                row.error(errors, COL_CHAPTER, "Tên xứ đoàn tối đa 255 ký tự");
            }

            if (errors.count() == before && deanery != null) {
                result.add(new Row(row, name, unitType, deanery, existingParent, parentKey, patronSaintDay, establishmentDate, chaplain, phone, email, chapter));
            }
        }
        // Giáo xứ ghi trước để giáo họ / giáo điểm trong file tìm được giáo xứ cha vừa tạo (sort ổn định, giữ thứ tự dòng).
        result.sort((a, b) -> Boolean.compare(a.unitType() != ParishUnitType.PARISH, b.unitType() != ParishUnitType.PARISH));
        return result;
    }

    private static String deaneryName(ExcelRowData row) {
        String text = row.text(COL_DEANERY);
        return text == null ? "" : text;
    }

    private static ParishUnitType parseType(String text) {
        return switch (ExcelWorkbookReader.normalize(text)) {
            case "giao xu" -> ParishUnitType.PARISH;
            case "giao ho" -> ParishUnitType.GIAO_HO;
            case "giao diem" -> ParishUnitType.GIAO_DIEM;
            default -> null;
        };
    }

    // Dùng để dựng tập giáo xứ trong file: chỉ nhận giáo hạt xác định duy nhất, mọi lỗi giáo hạt được báo ở bước resolveDeanery.
    private static Deanery uniqueDeanery(Map<String, List<Deanery>> byName, String deaneryName) {
        List<Deanery> matches = byName.getOrDefault(ExcelWorkbookReader.normalize(deaneryName), List.of());
        return matches.size() == 1 ? matches.get(0) : null;
    }

    // Giáo hạt bắt buộc và phải CÓ SẴN trong hệ thống, đồng thời nằm trong phạm vi tạo giáo xứ của tài khoản.
    private Deanery resolveDeanery(ExcelRowData row, Map<String, List<Deanery>> byName, Map<UUID, String> problems,
                                   Authentication authentication, ImportErrors errors) {
        String deaneryName = row.requiredText(errors, COL_DEANERY);
        if (deaneryName == null) {
            return null;
        }
        List<Deanery> matches = byName.getOrDefault(ExcelWorkbookReader.normalize(deaneryName), List.of());
        if (matches.isEmpty()) {
            row.error(errors, COL_DEANERY, "Giáo hạt '" + deaneryName + "' chưa có trong hệ thống");
            return null;
        }
        List<Deanery> allowed = new ArrayList<>();
        String denied = null;
        for (Deanery candidate : matches) {
            String problem = problems.computeIfAbsent(candidate.getId(), id -> checkDeanery(candidate, authentication));
            if (problem.isEmpty()) {
                allowed.add(candidate);
            } else {
                denied = problem;
            }
        }
        if (allowed.size() == 1) {
            return allowed.get(0);
        }
        if (allowed.size() > 1) {
            row.error(errors, COL_DEANERY, "Có nhiều giáo hạt tên '" + deaneryName + "', không xác định được giáo hạt nào");
        } else {
            row.error(errors, COL_DEANERY, denied != null ? denied : "Giáo hạt '" + deaneryName + "' không dùng được");
        }
        return null;
    }

    // Chuỗi rỗng nghĩa là hợp lệ; ngược lại là lý do không tạo được giáo xứ trong giáo hạt này.
    private String checkDeanery(Deanery deanery, Authentication authentication) {
        try {
            ParishCreateRequest probe = new ParishCreateRequest();
            probe.setDeaneryId(deanery.getId());
            parishScopeHelper.enforceCreateScope(probe, authentication);
            if (unitOperationalStatusService != null) {
                unitOperationalStatusService.requireUnitOperational(UnitType.DEANERY, deanery.getId());
            }
            return "";
        } catch (ApiException exception) {
            return exception.getMessage() == null ? "Không thể tạo giáo xứ trong giáo hạt này" : exception.getMessage();
        }
    }

    @Override
    protected void beforeWrite(ImportRun run) {
        run.now = OffsetDateTime.now();
    }

    @Override
    protected void write(Row item, ImportRun run) {
        Parish parent = item.existingParent() != null ? item.existingParent() : run.created.get(item.parentKey());
        Parish parish = new Parish();
        parish.setUnitType(item.unitType());
        parish.setParentParish(item.unitType() == ParishUnitType.PARISH ? null : parent);
        parish.setDeanery(item.deanery());
        parish.setName(item.name());
        parish.setPatronSaintDay(item.patronSaintDay());
        parish.setEstablishmentDate(item.establishmentDate());
        parish.setChaplain(item.chaplain());
        parish.setPhoneNumber(item.phone());
        parish.setEmail(item.email());
        parish.setChapterName(item.chapter());
        parish.setStatus(Boolean.TRUE);
        parish.setCreatedAt(run.now);
        parish.setUpdatedAt(run.now);
        parish.setCreatedBy(run.actor);
        parish.setUpdatedBy(run.actor);
        parishRepository.save(parish);
        run.created.put(item.deanery().getId() + "|" + ExcelWorkbookReader.normalize(item.name()), parish);
    }

    @Override
    protected ImportSummary summarize(int rowCount, ImportRun run) {
        return new ImportSummary(rowCount, "Đã import " + rowCount + " giáo xứ", List.of(), List.of());
    }
}
