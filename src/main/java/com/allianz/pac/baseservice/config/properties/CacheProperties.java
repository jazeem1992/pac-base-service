package com.allianz.pac.baseservice.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Redis cache settings ({@code app.cache.*}).
 *
 * @param keyPrefix  prefix of every Redis key (isolates services and environments sharing a cluster)
 * @param defaultTtl TTL used for caches without an explicit entry in {@code ttl}
 * @param ttl        TTL per cache name
 */
@Validated
@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(@NotBlank String keyPrefix, @NotNull Duration defaultTtl, Map<String, Duration> ttl) {

    /**
     * Normalises the optional map so callers never deal with {@code null}.
     */
    public CacheProperties {
        ttl = ttl == null ? Map.of() : Map.copyOf(ttl);
    }

    /**
     * Returns the TTL of a cache, falling back to the default TTL.
     *
     * @param cacheName cache name
     * @return the configured TTL
     */
    public Duration ttlFor(String cacheName) {
        return ttl.getOrDefault(cacheName, defaultTtl);
    }
}
