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

import java.util.UUID;

// Điểm của khóa chỉ hiện cho cấp sở hữu khóa (giáo phận với khóa giáo phận, giáo hạt với khóa giáo hạt) hoặc cấp cao hơn.
// Các cấp còn lại (giáo xứ, chính huynh trưởng) chỉ thấy khi khóa bật "hiển thị điểm".
@Component
@RequiredArgsConstructor
public class CourseScoreVisibility {

    private static final String SUPER_ADMIN_AUTHORITY = "ROLE_SUPER_ADMIN";
    private static final String VIEWER_ATTRIBUTE = CourseScoreVisibility.class.getName() + ".viewer";

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
            return null;
        }
        Object cached = attributes.getAttribute(VIEWER_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (cached instanceof Viewer viewer) {
            return viewer;
        }
        String username = securityUtils.currentUsername(authentication);
        Viewer viewer = new Viewer(
                securityUtils.hasAuthority(authentication, SUPER_ADMIN_AUTHORITY),
                username == null ? null : accountRepository.findDirectActiveDioceseIdByUsername(username).orElse(null),
                username == null ? null : accountRepository.findDirectActiveDeaneryIdByUsername(username).orElse(null));
        attributes.setAttribute(VIEWER_ATTRIBUTE, viewer, RequestAttributes.SCOPE_REQUEST);
        return viewer;
    }

    private record Viewer(boolean superAdmin, UUID dioceseId, UUID deaneryId) {
    }
}
