package phucuong.ht.config;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Các chú thích gộp để xóa cache khi dữ liệu liên quan thay đổi. Gắn lên mọi phương thức ghi làm đổi dữ liệu
// mà cache danh sách / chi tiết / thống kê đang giữ, thay vì tự liệt kê lại từng tên cache ở từng nơi.
public final class EvictCaches {

    private EvictCaches() {
    }

    // Danh sách và chi tiết khóa huấn luyện (gồm số hồ sơ đã duyệt, trạng thái, loại khóa).
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_DETAIL, allEntries = true)
    })
    public @interface Courses {
    }

    // Danh sách và chi tiết chứng nhận (trạng thái, kết quả, cấp độ đạt được).
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.CERTIFICATE_DETAIL, allEntries = true)
    })
    public @interface Certificates {
    }

    // Danh sách và chi tiết huynh trưởng (cấp độ hiện tại đổi khi thăng cấp / hạ cấp).
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.LEADER_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.LEADER_DETAIL, allEntries = true)
    })
    public @interface Leaders {
    }

    // Số liệu thống kê tổng hợp từ huynh trưởng, khóa, hồ sơ tham dự và chứng nhận.
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.ANALYTICS_OVERVIEW, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.ANALYTICS_ORGANIZATION, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.ANALYTICS_LEADERS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.ANALYTICS_COURSES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.ANALYTICS_CERTIFICATES, allEntries = true)
    })
    public @interface Analytics {
    }
}
