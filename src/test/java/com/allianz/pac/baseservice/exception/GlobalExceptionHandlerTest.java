package com.allianz.pac.baseservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.allianz.pac.baseservice.common.response.ApiResponse;
import com.allianz.pac.baseservice.common.response.ResponseStatus;
import com.allianz.pac.baseservice.support.ResponseFactorySupport;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

/**
 * Unit tests for {@link GlobalExceptionHandler} covering exceptions that are hard to trigger
 * through HTTP calls (request-level mappings are covered by the controller and integration tests).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(ResponseFactorySupport.responseFactory());

    /**
     * Asserts the common failure envelope.
     *
     * @param response        response to check
     * @param expectedStatus  expected HTTP status
     * @param expectedMessage expected message
     */
    private static void assertFailure(ResponseEntity<ApiResponse<Map<String, String>>> response,
                                      int expectedStatus, String expectedMessage) {
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(ResponseStatus.FAILURE);
        assertThat(response.getBody().statusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody().message()).isEqualTo(expectedMessage);
    }

    /**
     * Verifies business exceptions map to 422 with the rule in the message.
     */
    @Test
    @DisplayName("BusinessException -> 422")
    void businessException() {
        assertFailure(handler.handleBaseException(new BusinessException("age must be over 18")),
                422, "Business rule violated: age must be over 18");
    }

    /**
     * Verifies business exceptions with explicit error code and details.
     */
    @Test
    @DisplayName("BusinessException with details returns them in data")
    void businessExceptionWithDetails() {
        ResponseEntity<ApiResponse<Map<String, String>>> response = handler.handleBaseException(
                new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, Map.of("age", "too young"), "age"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).containsEntry("age", "too young");
    }

    /**
     * Verifies downstream failures map to 502 / 504.
     */
    @Test
    @DisplayName("External service failures -> 502 / 504")
    void externalServiceFailures() {
        assertFailure(handler.handleBaseException(new ExternalServiceException(ErrorCode.EXTERNAL_SERVICE_ERROR, "svc", null)),
                502, "Dependent service 'svc' failed to process the request");
        assertFailure(handler.handleBaseException(new ExternalServiceException(ErrorCode.EXTERNAL_SERVICE_TIMEOUT, "svc", null)),
                504, "Dependent service 'svc' did not respond in time");
        assertFailure(handler.handleBaseException(new ExternalServiceClientException("svc", 400)),
                502, "Dependent service 'svc' failed to process the request");
    }

    /**
     * Verifies Resilience4j rejections map to 429 / 503.
     */
    @Test
    @DisplayName("Resilience4j rejections -> 429 / 503")
    void resilienceRejections() {
        assertFailure(handler.handleRateLimited(RequestNotPermitted.createRequestNotPermitted(RateLimiter.ofDefaults("t"))),
                429, "Too many requests, please retry later");
        assertFailure(handler.handleServiceUnavailable(
                        CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("t"))),
                503, "Service temporarily unavailable, please retry later");
        assertFailure(handler.handleServiceUnavailable(BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("t"))),
                503, "Service temporarily unavailable, please retry later");
    }

    /**
     * Verifies data layer failures map to 409.
     */
    @Test
    @DisplayName("Data integrity / optimistic locking -> 409")
    void dataConflicts() {
        assertFailure(handler.handleDataIntegrity(new DataIntegrityViolationException("dup")),
                409, "Request conflicts with existing data");
        assertFailure(handler.handleOptimisticLocking(new OptimisticLockingFailureException("stale")),
                409, "Resource was modified by another request, please reload and retry");
    }

    /**
     * Verifies request-level failures.
     */
    @Test
    @DisplayName("Request level failures -> 400 / 404 / 405 / 406 / 415 / 503")
    void requestFailures() {
        assertFailure(handler.handleMissingParameter(new MissingServletRequestParameterException("page", "int")),
                400, "Required parameter 'page' is missing");
        assertFailure(handler.handleNoEndpoint(new IllegalArgumentException("none")), 404, "Requested endpoint does not exist");
        assertFailure(handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PATCH")),
                405, "HTTP method not supported for this endpoint");
        assertFailure(handler.handleMediaTypeNotAcceptable(new HttpMediaTypeNotAcceptableException("xml")),
                406, "Requested response format is not supported");
        assertFailure(handler.handleMediaTypeNotSupported(new HttpMediaTypeNotSupportedException("xml")),
                415, "Content type not supported");
        assertFailure(handler.handleAsyncTimeout(new AsyncRequestTimeoutException()),
                503, "Request processing timed out, please retry later");
        assertFailure(handler.handleConstraintViolation(new ConstraintViolationException(Set.of())), 400, "Validation failed");
    }

    /**
     * Verifies that async wrapper exceptions are unwrapped to their cause.
     */
    @Test
    @DisplayName("CompletionException / ExecutionException are unwrapped")
    void unwrapsAsyncExceptions() {
        assertFailure(handler.handleAsyncWrapper(new CompletionException(new ResourceNotFoundException("Customer", "id", 1))),
                404, "Customer not found with id: 1");
        assertFailure(handler.handleAsyncWrapper(new ExecutionException(
                        RequestNotPermitted.createRequestNotPermitted(RateLimiter.ofDefaults("t")))),
                429, "Too many requests, please retry later");
        assertFailure(handler.handleAsyncWrapper(new CompletionException(
                        CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("t")))),
                503, "Service temporarily unavailable, please retry later");
        assertFailure(handler.handleAsyncWrapper(new CompletionException(
                        BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("t")))),
                503, "Service temporarily unavailable, please retry later");
        assertFailure(handler.handleAsyncWrapper(new CompletionException(new IllegalStateException("x"))),
                500, "An unexpected error occurred, please contact support with the correlation id");
    }

    /**
     * Verifies that unexpected exceptions never leak details.
     */
    @Test
    @DisplayName("Unexpected exception -> 500 with generic message")
    void unexpectedException() {
        ResponseEntity<ApiResponse<Map<String, String>>> response = handler.handleUnexpected(new IllegalStateException("db password=123"));

        assertFailure(response, 500, "An unexpected error occurred, please contact support with the correlation id");
        assertThat(response.getBody().data()).isEmpty();
    }
}
