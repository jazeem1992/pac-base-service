package com.allianz.pac.baseservice.common.response;

import com.allianz.pac.baseservice.common.constants.AppConstants;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;

/**
 * Common response envelope returned by EVERY endpoint, for both success and failure.
 *
 * <pre>
 * {
 *   "status": "SUCCESS" | "FAILURE",
 *   "statusCode": 200,
 *   "message": "Customer fetched successfully",
 *   "data": { ... },
 *   "timestamp": "2026-11-10T10:15:30.123Z",
 *   "correlationId": "a1b2c3d4-..."
 * }
 * </pre>
 *
 * <p>{@code data} is never {@code null}: when there is no payload an empty object {@code {}} is
 * returned. For validation failures {@code data} holds a {@code field -> message} map.
 *
 * @param status        overall outcome of the call
 * @param statusCode    HTTP status code (also set on the HTTP response itself)
 * @param message       human readable, localized message
 * @param data          response payload, or error details for failures
 * @param timestamp     time the response was created (UTC, ISO-8601)
 * @param correlationId id that correlates this response with the server logs
 * @param <T>           payload type
 */
@JsonPropertyOrder({"status", "statusCode", "message", "data", "timestamp", "correlationId"})
public record ApiResponse<T>(
        ResponseStatus status,
        int statusCode,
        String message,
        T data,
        Instant timestamp,
        String correlationId) {

    /**
     * Creates a successful response for the given payload.
     *
     * @param statusCode HTTP status code of the response
     * @param message    resolved success message
     * @param data       payload; {@code null} is replaced by an empty object
     * @param <T>        payload type
     * @return the success envelope
     */
    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> success(int statusCode, String message, T data) {
        T payload = data != null ? data : (T) Map.of();
        return new ApiResponse<>(ResponseStatus.SUCCESS, statusCode, message, payload, Instant.now(), currentCorrelationId());
    }

    /**
     * Creates a failure response.
     *
     * @param statusCode HTTP status code of the response
     * @param message    resolved error message
     * @param details    optional error details (e.g. field errors); {@code null} becomes {@code {}}
     * @return the failure envelope
     */
    public static ApiResponse<Map<String, String>> failure(int statusCode, String message, Map<String, String> details) {
        Map<String, String> payload = details != null ? details : Map.of();
        return new ApiResponse<>(ResponseStatus.FAILURE, statusCode, message, payload, Instant.now(), currentCorrelationId());
    }

    /**
     * Reads the correlation id of the current request from the logging MDC.
     *
     * @return the correlation id, or {@code null} when called outside a request
     */
    private static String currentCorrelationId() {
        return MDC.get(AppConstants.CORRELATION_ID_MDC_KEY);
    }
}
