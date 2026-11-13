package phucuong.ht.common.excel;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import phucuong.ht.common.repository.BaseSearchRequest;
import phucuong.ht.common.security.AccountScopeResolver;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.deanery.model.entity.Deanery;
import phucuong.ht.modules.deanery.repository.DeaneryRepository;
import phucuong.ht.modules.diocese.model.entity.Diocese;
import phucuong.ht.modules.diocese.repository.DioceseRepository;
import phucuong.ht.modules.parish.model.entity.Parish;
import phucuong.ht.modules.parish.repository.ParishRepository;
import phucuong.ht.modules.system.model.entity.Account;
import phucuong.ht.modules.training.trainingCourse.repository.TrainingCourseRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Dựng phần đầu file xuất: dòng GIÁO PHẬN / GIÁO HẠT / GIÁO XỨ và dòng tiêu đề nội dung.
 * Đơn vị lấy theo bộ lọc đang chọn trước (lọc một giáo xứ / giáo hạt / giáo phận); không lọc thì theo đơn vị của tài khoản
 * (tài khoản giáo phận: chỉ dòng giáo phận; giáo hạt: thêm dòng giáo hạt; giáo xứ: đủ ba dòng). Super admin không có đơn vị.
 * Khi danh sách đang lọc theo một khóa ({@code course.courseCode}), tiêu đề được nối thêm tên khóa.
 */
@Component
@RequiredArgsConstructor
public class ExcelReportHeaderResolver {

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;
    private final DioceseRepository dioceseRepository;
    private final DeaneryRepository deaneryRepository;
    private final ParishRepository parishRepository;
    private final TrainingCourseRepository trainingCourseRepository;

    /** listTitle vd. "Danh sách tham dự"; withCourse: nối tên khóa đang lọc vào tiêu đề. */
    @Transactional(readOnly = true)
    public ExcelReportHeader resolve(BaseSearchRequest request, Authentication authentication, String listTitle, boolean withCourse) {
        Map<String, Object> filters = request == null || request.getFilters() == null ? Map.of() : request.getFilters();
        String title = listTitle;
        if (withCourse) {
            String courseName = courseName(filters.get("course.courseCode"));
            if (courseName != null) {
                title = listTitle + " " + (courseName.toLowerCase(Locale.ROOT).startsWith("khóa") ? courseName : "khóa " + courseName);
            }
        }
        return new ExcelReportHeader(unitLines(filters, authentication), title.toUpperCase(new Locale("vi", "VN")));
    }

    public String courseName(Object courseCode) {
        Object code = single(courseCode);
        if (code == null || String.valueOf(code).isBlank()) {
            return null;
        }
        return trainingCourseRepository.findByCodeWithType(String.valueOf(code)).map(course -> course.getCourseName()).orElse(null);
    }

    private List<String> unitLines(Map<String, Object> filters, Authentication authentication) {
        UUID parishId = idFilter(filters, "parish.id", "parishId");
        if (parishId != null) {
            Parish parish = parishRepository.findById(parishId).orElse(null);
            if (parish != null) {
                return lines(parish.getDeanery() == null ? null : parish.getDeanery().getDiocese(), parish.getDeanery(), parish);
            }
        }
        UUID deaneryId = idFilter(filters, "deanery.id", "deaneryId");
        if (deaneryId != null) {
            Deanery deanery = deaneryRepository.findById(deaneryId).orElse(null);
            if (deanery != null) {
                return lines(deanery.getDiocese(), deanery, null);
            }
        }
        UUID dioceseId = idFilter(filters, "diocese.id", "dioceseId");
        if (dioceseId != null) {
            Diocese diocese = dioceseRepository.findById(dioceseId).orElse(null);
            if (diocese != null) {
                return lines(diocese, null, null);
            }
        }
        return accountLines(authentication);
    }

    private List<String> accountLines(Authentication authentication) {
        String username = securityUtils.currentUsername(authentication);
        if (username == null || username.isBlank()) {
            return List.of();
        }
        Account account = accountRepository.findActiveByUsername(username).orElse(null);
        if (account == null) {
            return List.of();
        }
        // Đơn vị suy ra từ phạm vi hiệu lực (gắn trực tiếp hoặc qua huynh trưởng); số dòng in theo cấp của tài khoản (lấy từ quyền).
        Parish parish = account.getParish() != null ? account.getParish()
                : account.getLeader() != null ? account.getLeader().getParish() : null;
        Deanery deanery = account.getDeanery() != null ? account.getDeanery() : parish == null ? null : parish.getDeanery();
        Diocese diocese = account.getDiocese() != null ? account.getDiocese() : deanery == null ? null : deanery.getDiocese();
        AccountScopeResolver.Level level = AccountScopeResolver.levelOf(authentication);
        if (level == AccountScopeResolver.Level.DIOCESE) {
            return lines(diocese, null, null);
        }
        if (level == AccountScopeResolver.Level.DEANERY) {
            return lines(diocese, deanery, null);
        }
        return lines(diocese, deanery, parish);
    }

    private static List<String> lines(Diocese diocese, Deanery deanery, Parish parish) {
        Locale vi = new Locale("vi", "VN");
        List<String> lines = new ArrayList<>();
        if (diocese != null) {
            lines.add(("Giáo phận " + diocese.getName()).toUpperCase(vi));
        }
        if (deanery != null) {
            lines.add(("Giáo hạt " + deanery.getName()).toUpperCase(vi));
        }
        if (parish != null) {
            String label = parish.getUnitType() == null ? "Giáo xứ" : parish.getUnitType().getLabel();
            String name = label + " " + parish.getName();
            if (parish.getParentParish() != null) {
                name += " (Giáo xứ " + parish.getParentParish().getName() + ")";
            }
            lines.add(name.toUpperCase(vi));
        }
        return lines;
    }

    /** Lấy UUID từ bộ lọc có tên kết thúc bằng một trong các hậu tố (vd. leader.parish.id); bỏ qua khi lọc nhiều giá trị. */
    private static UUID idFilter(Map<String, Object> filters, String... suffixes) {
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String key = entry.getKey();
            for (String suffix : suffixes) {
                if (key.equals(suffix) || key.endsWith("." + suffix)) {
                    UUID id = uuid(single(entry.getValue()));
                    if (id != null) {
                        return id;
                    }
                }
            }
        }
        return null;
    }

    private static Object single(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.size() == 1 ? collection.iterator().next() : null;
        }
        if (value instanceof String text && text.contains(",")) {
            return null;
        }
        return value;
    }

    private static UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value).trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
