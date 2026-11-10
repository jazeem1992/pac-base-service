package com.allianz.pac.baseservice.common.constants;

/**
 * Names of all caches used by the service.
 *
 * <p>Every cache must be registered here and in {@code CacheConfig} together with its value type;
 * the TTL of each cache is configured in {@code app.cache.ttl.<cache-name>}.
 */
public final class CacheNames {

    /** Cache of single customers keyed by customer id. */
    public static final String CUSTOMERS = "customers";

    /**
     * Prevents instantiation of this constants holder.
     */
    private CacheNames() {
        throw new UnsupportedOperationException("Constants class must not be instantiated");
    }
}
