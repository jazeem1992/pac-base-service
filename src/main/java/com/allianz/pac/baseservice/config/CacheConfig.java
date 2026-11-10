package com.allianz.pac.baseservice.config;

import com.allianz.pac.baseservice.common.constants.CacheNames;
import com.allianz.pac.baseservice.config.properties.CacheProperties;
import com.allianz.pac.baseservice.dto.response.CustomerResponse;
import java.util.Map;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis cache configuration.
 *
 * <ul>
 *   <li>every cache is registered explicitly with its value type and TTL ({@code app.cache.ttl.*}),</li>
 *   <li>values are stored as JSON using the application JsonMapper,</li>
 *   <li>keys are prefixed with {@code app.cache.key-prefix} (service + environment isolation),</li>
 *   <li>cache writes are transaction-aware (applied only after a successful commit),</li>
 *   <li>Redis failures are logged and ignored so an unavailable cache never breaks the API.</li>
 * </ul>
 */
@EnableCaching
@Configuration(proxyBeanMethods = false)
public class CacheConfig implements CachingConfigurer {

    /** Value type of every cache. Register each new cache here. */
    private static final Map<String, Class<?>> CACHE_VALUE_TYPES = Map.of(
            CacheNames.CUSTOMERS, CustomerResponse.class);

    /**
     * Logs cache errors (e.g. Redis down) instead of failing the request; the method then simply
     * executes without cache.
     *
     * @return the cache error handler
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(CacheConfig.class.getName(), false);
    }

    /**
     * Default Redis cache configuration: default TTL, key prefix, string keys, no null values.
     *
     * @param properties cache properties
     * @return the default cache configuration
     */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(CacheProperties properties) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.defaultTtl())
                .prefixCacheNameWith(properties.keyPrefix())
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()));
    }

    /**
     * Registers every known cache with its own TTL and typed JSON serializer and makes the cache
     * manager transaction-aware. Unknown cache names are rejected.
     *
     * @param defaults   default cache configuration
     * @param properties cache properties
     * @param jsonMapper application JSON mapper
     * @return the customizer applied by Spring Boot to the Redis cache manager
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(RedisCacheConfiguration defaults,
                                                                                 CacheProperties properties,
                                                                                 JsonMapper jsonMapper) {
        return builder -> {
            builder.transactionAware().disableCreateOnMissingCache();
            CACHE_VALUE_TYPES.forEach((cacheName, valueType) -> builder.withCacheConfiguration(cacheName,
                    defaults.entryTtl(properties.ttlFor(cacheName))
                            .serializeValuesWith(SerializationPair.fromSerializer(jsonSerializer(jsonMapper, valueType)))));
        };
    }

    /**
     * Creates a typed JSON serializer for cache values.
     *
     * @param jsonMapper application JSON mapper
     * @param valueType  type of the cached values
     * @param <T>        value type
     * @return the serializer
     */
    private static <T> JacksonJsonRedisSerializer<T> jsonSerializer(JsonMapper jsonMapper, Class<T> valueType) {
        return new JacksonJsonRedisSerializer<>(jsonMapper, valueType);
    }
}
