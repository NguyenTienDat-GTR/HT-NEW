package phucuong.ht.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final String CACHE_PREFIX = "ht:api-cache:";

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        ObjectMapper redisObjectMapper = objectMapper.copy().findAndRegisterModules();
        SimpleModule pageModule = new SimpleModule();
        pageModule.addDeserializer(PageImpl.class, new PageImplJsonDeserializer());
        redisObjectMapper.registerModule(pageModule);

        GenericJackson2JsonRedisSerializer serializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(redisObjectMapper)
                .defaultTyping(true)
                .build();

        RedisCacheConfiguration baseConfig = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(cacheName -> CACHE_PREFIX + cacheName + "::")
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        addCaches(cacheConfigurations, baseConfig, Duration.ofMinutes(10),
                CacheNames.DIOCESE_LIST, CacheNames.DIOCESE_DETAIL,
                CacheNames.DEANERY_LIST, CacheNames.DEANERY_DETAIL,
                CacheNames.PARISH_LIST, CacheNames.PARISH_DETAIL,
                CacheNames.POSITION_LIST, CacheNames.POSITION_DETAIL);
        addCaches(cacheConfigurations, baseConfig, Duration.ofMinutes(5),
                CacheNames.LEADER_LIST, CacheNames.LEADER_DETAIL,
                CacheNames.EXECUTIVE_ASSIGNMENT_LIST, CacheNames.EXECUTIVE_ASSIGNMENT_DETAIL);
        addCaches(cacheConfigurations, baseConfig, Duration.ofMinutes(3),
                CacheNames.TRAINING_COURSE_LIST, CacheNames.TRAINING_COURSE_DETAIL,
                CacheNames.COURSE_REQUIREMENT_LIST, CacheNames.COURSE_REQUIREMENT_DETAIL,
                CacheNames.CERTIFICATE_LIST, CacheNames.CERTIFICATE_DETAIL,
                CacheNames.ANALYTICS_OVERVIEW, CacheNames.ANALYTICS_ORGANIZATION,
                CacheNames.ANALYTICS_LEADERS, CacheNames.ANALYTICS_COURSES,
                CacheNames.ANALYTICS_CERTIFICATES);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(baseConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    private void addCaches(
            Map<String, RedisCacheConfiguration> cacheConfigurations,
            RedisCacheConfiguration baseConfig,
            Duration ttl,
            String... names
    ) {
        for (String name : names) {
            cacheConfigurations.put(name, baseConfig.entryTtl(ttl));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class PageImplJsonDeserializer extends JsonDeserializer<PageImpl> {

        @Override
        public PageImpl deserialize(JsonParser parser, com.fasterxml.jackson.databind.DeserializationContext context) throws IOException {
            ObjectMapper mapper = (ObjectMapper) parser.getCodec();
            JsonNode root = mapper.readTree(parser);
            JsonNode contentNode = unwrapDefaultTypedValue(root.path("content"));
            List<Object> content = new ArrayList<>();

            if (contentNode.isArray()) {
                for (JsonNode item : contentNode) {
                    content.add(mapper.treeToValue(item, Object.class));
                }
            }

            int pageNumber = Math.max(root.path("number").asInt(0), 0);
            int pageSize = root.path("size").asInt(content.isEmpty() ? 1 : content.size());
            if (pageSize < 1) {
                pageSize = 1;
            }
            long totalElements = root.path("totalElements").asLong(content.size());

            return new PageImpl(content, PageRequest.of(pageNumber, pageSize), totalElements);
        }

        private static JsonNode unwrapDefaultTypedValue(JsonNode node) {
            if (node.isArray() && node.size() == 2 && node.get(0).isTextual()) {
                return node.get(1);
            }
            return node;
        }
    }
}
