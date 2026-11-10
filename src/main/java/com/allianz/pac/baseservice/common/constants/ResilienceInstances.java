package com.allianz.pac.baseservice.common.constants;

/**
 * Names of Resilience4j instances used with {@code @Retry}, {@code @CircuitBreaker},
 * {@code @Bulkhead}, {@code @RateLimiter} and {@code @TimeLimiter}.
 *
 * <p>The behaviour of every instance (attempts, back-off, thresholds, limits) is configured under
 * {@code resilience4j.*.instances.<name>} in the property files.
 */
public final class ResilienceInstances {

    /** Instance protecting calls to the downstream credit-score service. */
    public static final String CREDIT_SCORE_SERVICE = "creditScoreService";

    /** Instance rate limiting the public customer API. */
    public static final String CUSTOMER_API = "customerApi";

    /**
     * Prevents instantiation of this constants holder.
     */
    private ResilienceInstances() {
        throw new UnsupportedOperationException("Constants class must not be instantiated");
    }
}
