package com.allianz.pac.baseservice.common.response;

import com.allianz.pac.baseservice.util.MessageResolver;
import com.allianz.pac.baseservice.exception.ErrorCode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Builds {@link ApiResponse} envelopes wrapped in {@link ResponseEntity} with localized messages.
 *
 * <p>Controllers, the global exception handler and the error controller must use this factory
 * so that every response of the service has exactly the same shape.
 */
@Component
@RequiredArgsConstructor
public class ApiResponseFactory {

    private final MessageResolver messageResolver;

    /**
     * Builds a {@code 200 OK} success response.
     *
     * @param messageKey key of the success message in {@code messages.properties}
     * @param data       payload
     * @param args       optional message arguments
     * @param <T>        payload type
     * @return the HTTP response with the success envelope
     */
    public <T> ResponseEntity<ApiResponse<T>> ok(String messageKey, T data, Object... args) {
        return success(HttpStatus.OK, messageKey, data, args);
    }

    /**
     * Builds a {@code 201 Created} success response.
     *
     * @param messageKey key of the success message in {@code messages.properties}
     * @param data       payload
     * @param args       optional message arguments
     * @param <T>        payload type
     * @return the HTTP response with the success envelope
     */
    public <T> ResponseEntity<ApiResponse<T>> created(String messageKey, T data, Object... args) {
        return success(HttpStatus.CREATED, messageKey, data, args);
    }

    /**
     * Builds a success response with an arbitrary status code.
     *
     * @param status     HTTP status of the response
     * @param messageKey key of the success message in {@code messages.properties}
     * @param data       payload
     * @param args       optional message arguments
     * @param <T>        payload type
     * @return the HTTP response with the success envelope
     */
    public <T> ResponseEntity<ApiResponse<T>> success(HttpStatus status, String messageKey, T data, Object... args) {
        String message = messageResolver.resolve(messageKey, args);
        return ResponseEntity.status(status).body(ApiResponse.success(status.value(), message, data));
    }

    /**
     * Builds a failure response for the given error code.
     *
     * @param errorCode error code providing the HTTP status and message key
     * @param details   optional error details (e.g. field errors), may be {@code null}
     * @param args      optional message arguments
     * @return the HTTP response with the failure envelope
     */
    public ResponseEntity<ApiResponse<Map<String, String>>> failure(ErrorCode errorCode, Map<String, String> details, Object... args) {
        String message = messageResolver.resolve(errorCode.getMessageKey(), args);
        HttpStatus status = errorCode.getHttpStatus();
        return ResponseEntity.status(status).body(ApiResponse.failure(status.value(), message, details));
    }
}
