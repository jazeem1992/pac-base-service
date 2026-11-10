package com.allianz.pac.baseservice.exception;

import com.allianz.pac.baseservice.common.response.ApiResponse;
import com.allianz.pac.baseservice.common.response.ApiResponseFactory;
import com.allianz.pac.baseservice.util.LogSanitizer;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates every exception raised by a controller into the common {@link ApiResponse} envelope.
 *
 * <p>Logging policy: client errors (4xx) are logged at WARN without stack trace, server errors
 * (5xx) at ERROR with stack trace. Internal details are never exposed to the client.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final String ROOT_OBJECT_KEY = "request";

    private final ApiResponseFactory responseFactory;

    /**
     * Handles all application exceptions carrying an {@link ErrorCode}.
     *
     * @param ex the application exception
     * @return failure envelope with the status of the error code
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleBaseException(BaseException ex) {
        logFailure(ex.getErrorCode(), ex);
        return responseFactory.failure(ex.getErrorCode(), ex.getDetails(), ex.getMessageArgs());
    }

    /**
     * Handles Bean Validation failures of {@code @Valid @RequestBody} arguments.
     *
     * @param ex the validation exception
     * @return 400 failure envelope with a {@code field -> message} map in {@code data}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        return validationFailure(errors);
    }

    /**
     * Handles validation failures of {@code @PathVariable} / {@code @RequestParam} constraints.
     *
     * @param ex the method validation exception
     * @return 400 failure envelope with a {@code parameter -> message} map in {@code data}
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            String message = result.getResolvableErrors().stream()
                    .map(MessageSourceResolvable::getDefaultMessage)
                    .findFirst()
                    .orElse(null);
            errors.putIfAbsent(parameterName != null ? parameterName : ROOT_OBJECT_KEY, message);
        });
        return validationFailure(errors);
    }

    /**
     * Handles constraint violations raised by {@code @Validated} beans (e.g. services).
     *
     * @param ex the constraint violation exception
     * @return 400 failure envelope with a {@code property -> message} map in {@code data}
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(violation -> errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return validationFailure(errors);
    }

    /**
     * Handles unreadable request bodies (malformed JSON, wrong value types).
     *
     * @param ex the parsing exception
     * @return 400 failure envelope
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleNotReadable(HttpMessageNotReadableException ex) {
        return simpleFailure(ErrorCode.MALFORMED_REQUEST, ex);
    }

    /**
     * Handles a missing mandatory request parameter.
     *
     * @param ex the missing parameter exception
     * @return 400 failure envelope naming the missing parameter
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMissingParameter(MissingServletRequestParameterException ex) {
        logFailure(ErrorCode.MISSING_PARAMETER, ex);
        return responseFactory.failure(ErrorCode.MISSING_PARAMETER, null, ex.getParameterName());
    }

    /**
     * Handles a request parameter or path variable that cannot be converted to the target type.
     *
     * @param ex the type mismatch exception
     * @return 400 failure envelope naming the invalid parameter
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        logFailure(ErrorCode.TYPE_MISMATCH, ex);
        return responseFactory.failure(ErrorCode.TYPE_MISMATCH, null, ex.getName());
    }

    /**
     * Handles a sort parameter referencing an unknown property.
     *
     * @param ex the property reference exception
     * @return 400 failure envelope naming the invalid property
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handlePropertyReference(PropertyReferenceException ex) {
        logFailure(ErrorCode.INVALID_SORT_PROPERTY, ex);
        return responseFactory.failure(ErrorCode.INVALID_SORT_PROPERTY, null, ex.getPropertyName());
    }

    /**
     * Handles an HTTP method that is not supported by the endpoint.
     *
     * @param ex the exception
     * @return 405 failure envelope
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return simpleFailure(ErrorCode.METHOD_NOT_ALLOWED, ex);
    }

    /**
     * Handles an unsupported request {@code Content-Type}.
     *
     * @param ex the exception
     * @return 415 failure envelope
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return simpleFailure(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ex);
    }

    /**
     * Handles an {@code Accept} header that cannot be satisfied.
     *
     * @param ex the exception
     * @return 406 failure envelope
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return simpleFailure(ErrorCode.NOT_ACCEPTABLE, ex);
    }

    /**
     * Handles requests to URLs that are not mapped to any endpoint.
     *
     * @param ex the exception
     * @return 404 failure envelope
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Map<String, String>>> handleNoEndpoint(Exception ex) {
        return simpleFailure(ErrorCode.ENDPOINT_NOT_FOUND, ex);
    }

    /**
     * Handles database constraint violations (unique keys, foreign keys, not-null).
     *
     * @param ex the exception
     * @return 409 failure envelope
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return simpleFailure(ErrorCode.DATA_INTEGRITY_VIOLATION, ex);
    }

    /**
     * Handles optimistic locking conflicts (entity modified concurrently).
     *
     * @param ex the exception
     * @return 409 failure envelope
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleOptimisticLocking(OptimisticLockingFailureException ex) {
        return simpleFailure(ErrorCode.CONCURRENT_MODIFICATION, ex);
    }

    /**
     * Handles requests rejected by a Resilience4j {@code @RateLimiter}.
     *
     * @param ex the exception
     * @return 429 failure envelope
     */
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleRateLimited(RequestNotPermitted ex) {
        return simpleFailure(ErrorCode.TOO_MANY_REQUESTS, ex);
    }

    /**
     * Handles calls rejected by an open {@code @CircuitBreaker} or a full {@code @Bulkhead}.
     *
     * @param ex the exception
     * @return 503 failure envelope
     */
    @ExceptionHandler({CallNotPermittedException.class, BulkheadFullException.class})
    public ResponseEntity<ApiResponse<Map<String, String>>> handleServiceUnavailable(RuntimeException ex) {
        return simpleFailure(ErrorCode.SERVICE_UNAVAILABLE, ex);
    }

    /**
     * Handles asynchronous requests that did not complete within {@code spring.mvc.async.request-timeout}.
     *
     * @param ex the exception
     * @return 503 failure envelope
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleAsyncTimeout(AsyncRequestTimeoutException ex) {
        return simpleFailure(ErrorCode.REQUEST_TIMEOUT, ex);
    }

    /**
     * Unwraps exceptions coming from asynchronous processing ({@code CompletableFuture}) and
     * handles the root cause like a synchronous exception.
     *
     * @param ex wrapper exception
     * @return failure envelope for the wrapped cause
     */
    @ExceptionHandler({CompletionException.class, ExecutionException.class})
    public ResponseEntity<ApiResponse<Map<String, String>>> handleAsyncWrapper(Exception ex) {
        Throwable cause = ex.getCause();
        return switch (cause) {
            case BaseException baseException -> handleBaseException(baseException);
            case RequestNotPermitted rateLimited -> handleRateLimited(rateLimited);
            case CallNotPermittedException callNotPermitted -> handleServiceUnavailable(callNotPermitted);
            case BulkheadFullException bulkheadFull -> handleServiceUnavailable(bulkheadFull);
            case null, default -> handleUnexpected(ex);
        };
    }

    /**
     * Last-resort handler for any unexpected exception. The cause is logged, a generic message is
     * returned to the client.
     *
     * @param ex the unexpected exception
     * @return 500 failure envelope
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleUnexpected(Exception ex) {
        return simpleFailure(ErrorCode.INTERNAL_ERROR, ex);
    }

    /**
     * Builds a 400 validation failure envelope.
     *
     * @param errors field errors to return in {@code data}
     * @return failure envelope
     */
    private ResponseEntity<ApiResponse<Map<String, String>>> validationFailure(Map<String, String> errors) {
        log.warn("Request failed [{}]: {}", ErrorCode.VALIDATION_FAILED, LogSanitizer.clean(errors));
        return responseFactory.failure(ErrorCode.VALIDATION_FAILED, errors);
    }

    /**
     * Logs the exception and builds a failure envelope without details.
     *
     * @param errorCode error code to return
     * @param ex        exception being handled
     * @return failure envelope
     */
    private ResponseEntity<ApiResponse<Map<String, String>>> simpleFailure(ErrorCode errorCode, Exception ex) {
        logFailure(errorCode, ex);
        return responseFactory.failure(errorCode, null);
    }

    /**
     * Logs a handled failure according to its severity.
     *
     * @param errorCode error code returned to the client
     * @param ex        exception being handled
     */
    private void logFailure(ErrorCode errorCode, Exception ex) {
        if (errorCode.getHttpStatus().is5xxServerError()) {
            log.error("Request failed [{}]: {}", errorCode, LogSanitizer.clean(ex.getMessage()), ex);
        } else {
            log.warn("Request failed [{}]: {}", errorCode, LogSanitizer.clean(ex.getMessage()));
        }
    }
}
