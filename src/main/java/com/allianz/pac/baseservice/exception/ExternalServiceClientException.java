package com.allianz.pac.baseservice.exception;

import java.io.Serial;
import java.util.Map;
import lombok.Getter;

/**
 * Thrown when a downstream service rejects our request with a 4xx status.
 *
 * <p>This is NOT transient: it is excluded from retries and ignored by the circuit breaker,
 * because repeating the same request would fail again.
 */
@Getter
public class ExternalServiceClientException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String serviceName;
    private final int downstreamStatus;

    /**
     * Creates the exception.
     *
     * @param serviceName      logical name of the downstream service
     * @param downstreamStatus HTTP status returned by the downstream service
     */
    public ExternalServiceClientException(String serviceName, int downstreamStatus) {
        super(ErrorCode.EXTERNAL_SERVICE_ERROR, Map.of(), null, serviceName);
        this.serviceName = serviceName;
        this.downstreamStatus = downstreamStatus;
    }
}
