package phucuong.ht.modules.deanery.service;

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
import phucuong.ht.modules.deanery.helper.DeaneryScopeHelper;
import phucuong.ht.modules.deanery.helper.DeaneryValidationHelper;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.model.request.DeaneryCreateRequest;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.deanery.security.DeaneryAccessEvaluator;
import phucuong.ht.modules.diocese.model.entity.Diocese;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Import danh sách giáo hạt từ Excel vào giáo phận hiện tại của tài khoản. Chỉ bắt buộc Tên giáo hạt, các cột khác để trống được.
 * Kiểm tra toàn bộ file trước; có bất kỳ dòng lỗi nào thì không lưu gì.
 */
@Service
@RequiredArgsConstructor
public class DeaneryImportService extends AbstractExcelImporter<DeaneryImportService.Row, DeaneryImportService.ImportRun> {

    public static final String COL_NAME = "Tên giáo hạt";
    public static final String COL_PATRON_SAINT_DAY = "Ngày bổn mạng";
    public static final String COL_ESTABLISHMENT_DATE = "Ngày thành lập";
    public static final String COL_CHAPLAIN = "Linh mục tuyên úy";
    public static final String COL_PHONE = "Số điện thoại";
    public static final String COL_EMAIL = "Email";
    public static final String COL_ASSOCIATION = "Tên hiệp đoàn";

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^0[1-9]\\d{8}$");

    private final DeaneryRepository deaneryRepository;
    private final DeaneryScopeHelper deaneryScopeHelper;
    private final DeaneryValidationHelper deaneryValidationHelper;
    private final CommonValidationHelper commonValidationHelper;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private UnitOperationalStatusService unitOperationalStatusService;

    record Row(ExcelRowData source, String name, LocalDate patronSaintDay, LocalDate establishmentDate, String chaplain,
               String phone, String email, String association) {
    }

    /** Trạng thái của một lần import (service dùng chung nhiều luồng nên không giữ trong field). */
    static final class ImportRun {
        private final Authentication authentication;
        private final String actor;
        private Diocese diocese;
        private OffsetDateTime now;

        ImportRun(Authentication authentication, String actor) {
            this.authentication = authentication;
            this.actor = actor;
        }
    }

    @EvictCaches.Analytics
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.DIOCESE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DIOCESE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.DEANERY_DETAIL, allEntries = true)
    })
    @Transactional
    public ImportSummary importDeaneries(Path file, Authentication authentication, JobProgress progress) {
        String actor = deaneryValidationHelper.getRequiredAuditActor(authentication);
        return runImport(file, progress, new ImportRun(authentication, actor));
    }

    @Override
    protected List<Row> readRows(ExcelWorkbookReader reader, ImportRun run, ImportErrors errors) {
        ExcelSheetData sheet = reader.firstSheet();
        sheet.requireColumns(errors, COL_NAME);
        errors.throwIfAny();
        if (sheet.rows().isEmpty()) {
            throw commonValidationHelper.validationError("File Excel không có dòng dữ liệu nào");
        }
        run.diocese = resolveDiocese(run.authentication);

        Set<String> existing = new HashSet<>();
        for (String name : deaneryRepository.findNamesByDioceseId(run.diocese.getId())) {
            existing.add(ExcelWorkbookReader.normalize(name));
        }
        Set<String> seen = new HashSet<>();
        List<Row> result = new ArrayList<>();
        for (ExcelRowData row : sheet.rows()) {
            int before = errors.count();
            String name = row.requiredText(errors, COL_NAME);
            if (name != null) {
                if (name.length() > 100) {
                    row.error(errors, COL_NAME, "Tên giáo hạt tối đa 100 ký tự");
                } else {
                    String key = ExcelWorkbookReader.normalize(name);
                    if (existing.contains(key)) {
                        row.error(errors, COL_NAME, "Giáo hạt '" + name + "' đã có trong giáo phận");
                    } else if (!seen.add(key)) {
                        row.error(errors, COL_NAME, "Tên giáo hạt bị trùng với dòng khác trong file");
                    }
                }
            }
            LocalDate patronSaintDay = row.date(errors, COL_PATRON_SAINT_DAY, false);
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
            String association = row.text(COL_ASSOCIATION);
            if (association != null && association.length() > 255) {
                row.error(errors, COL_ASSOCIATION, "Tên hiệp đoàn tối đa 255 ký tự");
            }
            if (errors.count() == before) {
                result.add(new Row(row, name, patronSaintDay, establishmentDate, chaplain, phone, email, association));
            }
        }
        return result;
    }

    // Giáo hạt mới luôn thuộc giáo phận đang hoạt động của tài khoản (cùng quy tắc phạm vi và trạng thái khi tạo thủ công).
    private Diocese resolveDiocese(Authentication authentication) {
        UUID dioceseId;
        try {
            dioceseId = deaneryScopeHelper.getRequiredActiveDioceseId(authentication, DeaneryAccessEvaluator.CREATE_DIOCESE);
        } catch (ApiException exception) {
            throw commonValidationHelper.validationError("Không xác định được giáo phận của tài khoản hiện tại để tạo giáo hạt");
        }
        DeaneryCreateRequest probe = new DeaneryCreateRequest();
        probe.setDioceseId(dioceseId);
        deaneryScopeHelper.enforceCreateScope(probe, authentication);
        Diocese diocese = deaneryValidationHelper.requireExistingDiocese(dioceseId);
        if (unitOperationalStatusService != null) {
            unitOperationalStatusService.requireUnitOperational(UnitType.DIOCESE, dioceseId);
        }
        return diocese;
    }

    @Override
    protected void beforeWrite(ImportRun run) {
        run.now = OffsetDateTime.now();
    }

    @Override
    protected void write(Row item, ImportRun run) {
        Deanery deanery = new Deanery();
        deanery.setDiocese(run.diocese);
        deanery.setName(item.name());
        deanery.setPatronSaintDay(item.patronSaintDay());
        deanery.setEstablishmentDate(item.establishmentDate());
        deanery.setChaplain(item.chaplain());
        deanery.setPhoneNumber(item.phone());
        deanery.setEmail(item.email());
        deanery.setAssociationName(item.association());
        deanery.setStatus(Boolean.TRUE);
        deanery.setCreatedAt(run.now);
        deanery.setUpdatedAt(run.now);
        deanery.setCreatedBy(run.actor);
        deanery.setUpdatedBy(run.actor);
        deaneryRepository.save(deanery);
    }

    @Override
    protected ImportSummary summarize(int rowCount, ImportRun run) {
        return new ImportSummary(rowCount, "Đã import " + rowCount + " giáo hạt", List.of(), List.of());
    }
}
