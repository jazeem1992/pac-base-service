package com.allianz.pac.baseservice.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Catalogue of all error conditions of the service.
 *
 * <p>Each entry defines the HTTP status returned to the client and the key of the localized message
 * in {@code messages.properties}. The enum name is written to the logs to ease troubleshooting.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ----- 4xx: client errors -----
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "error.validation.failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "error.request.malformed"),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "error.request.missing-parameter"),
    TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "error.request.type-mismatch"),
    INVALID_SORT_PROPERTY(HttpStatus.BAD_REQUEST, "error.request.invalid-sort"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "error.resource.not-found"),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "error.endpoint.not-found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "error.request.method-not-allowed"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "error.request.not-acceptable"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "error.resource.duplicate"),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "error.data.integrity-violation"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "error.data.concurrent-modification"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "error.request.unsupported-media-type"),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_CONTENT, "error.business.rule-violation"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "error.request.rate-limited"),

    // ----- 5xx: server / dependency errors -----
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal"),
    EXTERNAL_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "error.external.failure"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "error.service.unavailable"),
    EXTERNAL_SERVICE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "error.external.timeout"),
    REQUEST_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "error.request.timeout");

    private final HttpStatus httpStatus;
    private final String messageKey;

    /**
     * Finds the most suitable error code for a raw HTTP status, used when an error happens outside
     * of the MVC layer (e.g. inside a servlet filter).
     *
     * @param statusCode HTTP status code
     * @return the matching error code, {@link #INTERNAL_ERROR} when nothing matches
     */
    public static ErrorCode fromHttpStatus(int statusCode) {
        return switch (statusCode) {
            case 400 -> MALFORMED_REQUEST;
            case 404 -> ENDPOINT_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 429 -> TOO_MANY_REQUESTS;
            case 503 -> SERVICE_UNAVAILABLE;
            default -> INTERNAL_ERROR;
        };
    }
}
