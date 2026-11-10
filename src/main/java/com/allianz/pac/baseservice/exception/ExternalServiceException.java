package com.allianz.pac.baseservice.exception;

import java.io.Serial;
import java.util.Map;
import lombok.Getter;

/**
 * Thrown when a downstream service fails with a TRANSIENT error (5xx, timeout, connection error).
 *
 * <p>This exception is configured as retryable for Resilience4j {@code @Retry} and is recorded as a
 * failure by {@code @CircuitBreaker}.
 */
@Getter
public class ExternalServiceException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String serviceName;

    /**
     * Creates the exception.
     *
     * @param errorCode   {@link ErrorCode#EXTERNAL_SERVICE_ERROR} or {@link ErrorCode#EXTERNAL_SERVICE_TIMEOUT}
     * @param serviceName logical name of the downstream service
     * @param cause       root cause, may be {@code null}
     */
    public ExternalServiceException(ErrorCode errorCode, String serviceName, Throwable cause) {
        super(errorCode, Map.of(), cause, serviceName);
        this.serviceName = serviceName;
    }
}
