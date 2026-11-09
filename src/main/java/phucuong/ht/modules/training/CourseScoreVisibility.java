package phucuong.ht.modules.training;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import phucuong.ht.common.utils.securityUtils;
import phucuong.ht.modules.auth.repository.AccountRepository;
import phucuong.ht.modules.training.trainingCourse.model.entity.TrainingCourse;
import phucuong.ht.modules.training.trainingCourse.security.TrainingCourseAccessEvaluator;

import java.util.UUID;

// Điểm của khóa chỉ hiện cho cấp sở hữu khóa (giáo phận với khóa giáo phận, giáo hạt với khóa giáo hạt) hoặc cấp cao hơn.
// Các cấp còn lại (giáo xứ, chính huynh trưởng) chỉ thấy khi khóa bật "hiển thị điểm".
@Component
@RequiredArgsConstructor
public class CourseScoreVisibility {

    private static final String SUPER_ADMIN_AUTHORITY = "ROLE_SUPER_ADMIN";
    private static final String VIEWER_ATTRIBUTE = CourseScoreVisibility.class.getName() + ".viewer";

    private static final ThreadLocal<Viewer> THREAD_VIEWER = new ThreadLocal<>();

    private final securityUtils securityUtils;
    private final AccountRepository accountRepository;

    /** Người xem là tài khoản đang đăng nhập; ngoài request (tác vụ nền, hệ thống) thì không che điểm. */
    public boolean canSeeScores(TrainingCourse course) {
        if (course == null || Boolean.TRUE.equals(course.getScoreVisible())) {
            return true;
        }
        Viewer viewer = currentViewer();
        if (viewer == null || viewer.superAdmin()) {
            return true;
        }
        if (viewer.deaneryId() != null && course.getDeanery() != null && viewer.deaneryId().equals(course.getDeanery().getId())) {
            return true;
        }
        UUID courseDioceseId = course.getDiocese() != null ? course.getDiocese().getId()
                : course.getDeanery() != null && course.getDeanery().getDiocese() != null ? course.getDeanery().getDiocese().getId() : null;
        return viewer.dioceseId() != null && viewer.dioceseId().equals(courseDioceseId);
    }

    // Tra cứu đơn vị của người xem một lần cho mỗi request để danh sách nhiều dòng không bị truy vấn lặp.
    private Viewer currentViewer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            // Tác vụ nền (xuất Excel) chạy với quyền của người tạo nhưng không có request: vẫn phải che điểm như khi người đó tự xem.
            Viewer cachedViewer = THREAD_VIEWER.get();
            if (cachedViewer == null) {
                String name = securityUtils.currentUsername(authentication);
                cachedViewer = new Viewer(
                        securityUtils.hasAuthority(authentication, SUPER_ADMIN_AUTHORITY),
                        dioceseScopeOf(authentication, name),
                        deaneryScopeOf(authentication, name));
                THREAD_VIEWER.set(cachedViewer);
            }
            return cachedViewer;
        }
        Object cached = attributes.getAttribute(VIEWER_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (cached instanceof Viewer viewer) {
            return viewer;
        }
        String username = securityUtils.currentUsername(authentication);
        Viewer viewer = new Viewer(
                securityUtils.hasAuthority(authentication, SUPER_ADMIN_AUTHORITY),
                dioceseScopeOf(authentication, username),
                deaneryScopeOf(authentication, username));
        attributes.setAttribute(VIEWER_ATTRIBUTE, viewer, RequestAttributes.SCOPE_REQUEST);
        return viewer;
    }

    // Cấp sở hữu theo ROLE đang có (không chỉ đơn vị gán trực tiếp cho tài khoản): tài khoản có role phạm vi giáo phận / giáo hạt mà đơn vị suy ra
    // từ huynh trưởng gắn với tài khoản vẫn là cấp tổ chức khóa. Tài khoản chỉ có role giáo xứ / huynh trưởng thì không có phạm vi nào.
    private UUID dioceseScopeOf(Authentication authentication, String username) {
        if (username == null || !securityUtils.hasAuthority(authentication, TrainingCourseAccessEvaluator.READ_DIOCESE)) {
            return null;
        }
        return accountRepository.findActiveDioceseIdByUsername(username).orElse(null);
    }

    private UUID deaneryScopeOf(Authentication authentication, String username) {
        if (username == null || !securityUtils.hasAuthority(authentication, TrainingCourseAccessEvaluator.READ_DEANERY)) {
            return null;
        }
        return accountRepository.findActiveDeaneryIdByUsername(username).orElse(null);
    }

    /** Worker nền gọi khi kết thúc mỗi job để người xem của job này không rò sang job khác trên cùng luồng. */
    public static void clearThreadViewer() {
        THREAD_VIEWER.remove();
    }

    private record Viewer(boolean superAdmin, UUID dioceseId, UUID deaneryId) {
    }
}
