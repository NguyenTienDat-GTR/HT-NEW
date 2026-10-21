package phucuong.ht.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

// Kiểm tra các chú thích gộp xóa cache hoạt động thật qua proxy của Spring, kể cả khi dùng chung với @Caching.
class EvictCachesTest {

    static class Writer {
        @EvictCaches.Certificates
        @EvictCaches.Leaders
        @EvictCaches.Analytics
        public void writeCertificate() {
        }

        @Caching(evict = @CacheEvict(cacheNames = CacheNames.TRAINING_COURSE_LIST, allEntries = true))
        @EvictCaches.Courses
        @EvictCaches.Analytics
        public void writeCourse() {
        }
    }

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        Writer writer() {
            return new Writer();
        }
    }

    private static void seed(CacheManager manager, String... names) {
        for (String name : names) {
            Cache cache = manager.getCache(name);
            assertNotNull(cache);
            cache.put("k", "v");
        }
    }

    private static void assertEvicted(CacheManager manager, String... names) {
        for (String name : names) {
            assertNull(manager.getCache(name).get("k"), name + " phải bị xóa");
        }
    }

    @Test
    void composedAnnotationsEvictEveryNamedCache() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            CacheManager manager = context.getBean(CacheManager.class);
            Writer writer = context.getBean(Writer.class);
            String[] all = {
                    CacheNames.CERTIFICATE_LIST, CacheNames.CERTIFICATE_DETAIL,
                    CacheNames.LEADER_LIST, CacheNames.LEADER_DETAIL,
                    CacheNames.ANALYTICS_OVERVIEW, CacheNames.ANALYTICS_ORGANIZATION, CacheNames.ANALYTICS_LEADERS,
                    CacheNames.ANALYTICS_COURSES, CacheNames.ANALYTICS_CERTIFICATES,
                    CacheNames.TRAINING_COURSE_LIST, CacheNames.TRAINING_COURSE_DETAIL};
            seed(manager, all);

            writer.writeCertificate();

            assertEvicted(manager, CacheNames.CERTIFICATE_LIST, CacheNames.CERTIFICATE_DETAIL,
                    CacheNames.LEADER_LIST, CacheNames.LEADER_DETAIL,
                    CacheNames.ANALYTICS_OVERVIEW, CacheNames.ANALYTICS_ORGANIZATION, CacheNames.ANALYTICS_LEADERS,
                    CacheNames.ANALYTICS_COURSES, CacheNames.ANALYTICS_CERTIFICATES);
            // Cache khóa huấn luyện không bị động tới.
            assertNotNull(manager.getCache(CacheNames.TRAINING_COURSE_LIST).get("k"));
        }
    }

    @Test
    void composedAnnotationsWorkTogetherWithCachingBlock() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            CacheManager manager = context.getBean(CacheManager.class);
            seed(manager, CacheNames.TRAINING_COURSE_LIST, CacheNames.TRAINING_COURSE_DETAIL, CacheNames.ANALYTICS_COURSES,
                    CacheNames.CERTIFICATE_LIST);

            context.getBean(Writer.class).writeCourse();

            assertEvicted(manager, CacheNames.TRAINING_COURSE_LIST, CacheNames.TRAINING_COURSE_DETAIL, CacheNames.ANALYTICS_COURSES);
            assertNotNull(manager.getCache(CacheNames.CERTIFICATE_LIST).get("k"));
        }
    }
}
