package com.allianz.pac.baseservice.common.constants;

/**
 * Application-wide technical constants (MDC keys, request attribute names).
 *
 * <p>Only values that are part of the code contract belong here. Anything that can differ per
 * environment must live in the property files instead.
 */
public final class AppConstants {

    /** MDC key holding the correlation id of the current request. */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /** Request attribute holding the correlation id (survives async dispatches). */
    public static final String CORRELATION_ID_ATTRIBUTE = AppConstants.class.getName() + ".CORRELATION_ID";

    /** Request attribute holding the request start time in nanoseconds. */
    public static final String REQUEST_START_TIME_ATTRIBUTE = AppConstants.class.getName() + ".START_TIME";

    /** Placeholder written to logs in place of masked values. */
    public static final String MASK = "****";

    /**
     * Prevents instantiation of this constants holder.
     */
    private AppConstants() {
        throw new UnsupportedOperationException("Constants class must not be instantiated");
    }
}
