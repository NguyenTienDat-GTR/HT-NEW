package phucuong.ht.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final String CACHE_PREFIX = "ht:api-cache:";

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(
                objectMapper.copy().findAndRegisterModules()
        );

        RedisCacheConfiguration baseConfig = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(cacheName -> CACHE_PREFIX + cacheName + "::")
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        addCaches(cacheConfigurations, Duration.ofMinutes(10),
                CacheNames.DIOCESE_LIST, CacheNames.DIOCESE_DETAIL,
                CacheNames.DEANERY_LIST, CacheNames.DEANERY_DETAIL,
                CacheNames.PARISH_LIST, CacheNames.PARISH_DETAIL,
                CacheNames.POSITION_LIST, CacheNames.POSITION_DETAIL);
        addCaches(cacheConfigurations, Duration.ofMinutes(5),
                CacheNames.LEADER_LIST, CacheNames.LEADER_DETAIL,
                CacheNames.EXECUTIVE_ASSIGNMENT_LIST, CacheNames.EXECUTIVE_ASSIGNMENT_DETAIL);
        addCaches(cacheConfigurations, Duration.ofMinutes(3),
                CacheNames.TRAINING_COURSE_LIST, CacheNames.TRAINING_COURSE_DETAIL,
                CacheNames.COURSE_REQUIREMENT_LIST, CacheNames.COURSE_REQUIREMENT_DETAIL,
                CacheNames.CERTIFICATE_LIST, CacheNames.CERTIFICATE_DETAIL);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(baseConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    private void addCaches(Map<String, RedisCacheConfiguration> cacheConfigurations, Duration ttl, String... names) {
        for (String name : names) {
            cacheConfigurations.put(name, RedisCacheConfiguration.defaultCacheConfig()
                    .computePrefixWith(cacheName -> CACHE_PREFIX + cacheName + "::")
                    .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                            new GenericJackson2JsonRedisSerializer()
                    ))
                    .disableCachingNullValues()
                    .entryTtl(ttl));
        }
    }
}
