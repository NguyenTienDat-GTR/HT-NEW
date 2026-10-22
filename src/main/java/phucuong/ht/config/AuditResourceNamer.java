package phucuong.ht.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;

/**
 * Tên dễ đọc của đối tượng được thao tác (tên huynh trưởng kèm tên thánh, tên khóa, tên giáo xứ...) để người xem nhật ký
 * không phải tra UUID. Chỉ tra khóa chính trong danh sách trắng; lỗi tra cứu không bao giờ ảnh hưởng yêu cầu nghiệp vụ.
 */
@Component
public class AuditResourceNamer {

    private static final Logger log = LoggerFactory.getLogger(AuditResourceNamer.class);
    private static final int MAX_LENGTH = 255;

    // resource -> {câu lệnh, kiểu khóa: uuid | text}
    private static final Map<String, String[]> LOOKUPS = Map.of(
            "diocese", new String[]{"select name from diocese where id = :id", "uuid"},
            "deanery", new String[]{"select name from deanery where id = :id", "uuid"},
            "parish", new String[]{"select name from parish where id = :id", "uuid"},
            "leader", new String[]{"select trim(coalesce(holy_name, '') || ' ' || last_name || ' ' || first_name) from leader where id = :id", "uuid"},
            "course", new String[]{"select course_name from training_course where course_code = :id", "text"},
            "certificate", new String[]{"select certificate_code || coalesce(' - ' || certificate_name, '') from certificate where certificate_code = :id", "text"},
            "participation", new String[]{"select trim(coalesce(l.holy_name, '') || ' ' || l.last_name || ' ' || l.first_name) || ' - ' || coalesce(c.course_name, '') from course_participation cp left join leader l on l.id = cp.leader_id left join training_course c on c.course_code = cp.course_id where cp.id = :id", "text"},
            "account", new String[]{"select username from account where username = :id", "text"},
            "course_type", new String[]{"select type_name from training_course_type where type_code = :id", "text"}
    );

    @PersistenceContext
    private EntityManager entityManager;

    public String nameOf(String resource, String resourceId) {
        if (!StringUtils.hasText(resource) || !StringUtils.hasText(resourceId)) return null;
        String[] lookup = LOOKUPS.get(resource);
        if (lookup == null) return null;
        try {
            Object key = "uuid".equals(lookup[1]) ? UUID.fromString(resourceId) : resourceId;
            var rows = entityManager.createNativeQuery(lookup[0]).setParameter("id", key).setMaxResults(1).getResultList();
            if (rows.isEmpty() || rows.get(0) == null) return null;
            String name = String.valueOf(rows.get(0)).trim();
            return name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH) : name;
        } catch (RuntimeException exception) {
            log.debug("Không tra được tên đối tượng {} {}", resource, resourceId, exception);
            return null;
        }
    }
}
