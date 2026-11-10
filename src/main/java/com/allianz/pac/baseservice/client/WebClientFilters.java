package com.allianz.pac.baseservice.client;

import com.allianz.pac.baseservice.common.constants.AppConstants;
import com.allianz.pac.baseservice.util.LogSanitizer;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

/**
 * Reusable {@link ExchangeFilterFunction}s applied to every outbound WebClient call.
 */
@Slf4j
public final class WebClientFilters {

    /**
     * Prevents instantiation of this utility class.
     */
    private WebClientFilters() {
        throw new UnsupportedOperationException("Utility class must not be instantiated");
    }

    /**
     * Adds the current correlation id (from the MDC) as request header so the downstream service
     * logs with the same id.
     *
     * @param headerName name of the correlation id header
     * @return the filter function
     */
    public static ExchangeFilterFunction correlationIdPropagation(String headerName) {
        return (request, next) -> {
            String correlationId = MDC.get(AppConstants.CORRELATION_ID_MDC_KEY);
            if (correlationId == null || request.headers().containsHeader(headerName)) {
                return next.exchange(request);
            }
            return next.exchange(ClientRequest.from(request).header(headerName, correlationId).build());
        };
    }

    /**
     * Logs START / END / FAILED of every outbound call with status and duration.
     *
     * @param serviceName logical name of the downstream service, used as log label
     * @return the filter function
     */
    public static ExchangeFilterFunction outboundLogging(String serviceName) {
        return (request, next) -> {
            long start = System.nanoTime();
            String target = request.method() + " " + LogSanitizer.clean(request.url().getPath());
            log.info("OUTBOUND START [{}] {}", serviceName, target);
            return next.exchange(request)
                    .doOnNext(response -> log.info("OUTBOUND END [{}] {} - status {} - {} ms",
                            serviceName, target, response.statusCode().value(), elapsedMillis(start)))
                    .doOnError(ex -> log.warn("OUTBOUND FAILED [{}] {} after {} ms - {}: {}",
                            serviceName, target, elapsedMillis(start), ex.getClass().getSimpleName(),
                            LogSanitizer.clean(ex.getMessage())));
        };
    }

    /**
     * Computes elapsed milliseconds since the given {@link System#nanoTime()} value.
     *
     * @param startNanos start time in nanoseconds
     * @return elapsed time in milliseconds
     */
    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
