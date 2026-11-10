package com.allianz.pac.baseservice.client;

import com.allianz.pac.baseservice.exception.ErrorCode;
import com.allianz.pac.baseservice.exception.ExternalServiceClientException;
import com.allianz.pac.baseservice.exception.ExternalServiceException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

/**
 * Base class of all downstream HTTP clients.
 *
 * <p>Provides blocking helpers (cheap on virtual threads) and non-blocking {@link Mono} variants,
 * and translates every failure into the application exception model:
 * <ul>
 *   <li>4xx response -&gt; {@link ExternalServiceClientException} (not retried),</li>
 *   <li>5xx response, connection error -&gt; {@link ExternalServiceException} (retried),</li>
 *   <li>timeouts -&gt; {@link ExternalServiceException} with {@link ErrorCode#EXTERNAL_SERVICE_TIMEOUT}.</li>
 * </ul>
 * Subclasses add Resilience4j annotations ({@code @Retry}, {@code @CircuitBreaker},
 * {@code @Bulkhead}, ...) to their public methods.
 */
public abstract class BaseWebClient {

    private final WebClient webClient;
    private final String serviceName;

    /**
     * Creates the client.
     *
     * @param webClient   WebClient configured for the downstream service
     * @param serviceName logical service name used in errors and logs
     */
    protected BaseWebClient(WebClient webClient, String serviceName) {
        this.webClient = webClient;
        this.serviceName = serviceName;
    }

    /**
     * Performs a blocking GET request.
     *
     * @param uriTemplate  relative URI template
     * @param responseType expected response body type
     * @param uriVariables URI template variables
     * @param <R>          response type
     * @return the response body (never {@code null})
     */
    protected <R> R get(String uriTemplate, Class<R> responseType, Object... uriVariables) {
        return requireBody(getAsync(uriTemplate, responseType, uriVariables).block());
    }

    /**
     * Performs a blocking GET request for generic response types (e.g. lists).
     *
     * @param uriTemplate  relative URI template
     * @param responseType expected response body type
     * @param uriVariables URI template variables
     * @param <R>          response type
     * @return the response body (never {@code null})
     */
    protected <R> R get(String uriTemplate, ParameterizedTypeReference<R> responseType, Object... uriVariables) {
        Mono<R> response = webClient.get().uri(uriTemplate, uriVariables)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> Mono.error(toException(clientResponse.statusCode())))
                .bodyToMono(responseType);
        return requireBody(translateErrors(response).block());
    }

    /**
     * Performs a blocking POST request with a JSON body.
     *
     * @param uriTemplate  relative URI template
     * @param body         request body
     * @param responseType expected response body type
     * @param uriVariables URI template variables
     * @param <B>          request body type
     * @param <R>          response type
     * @return the response body (never {@code null})
     */
    protected <B, R> R post(String uriTemplate, B body, Class<R> responseType, Object... uriVariables) {
        return requireBody(postAsync(uriTemplate, body, responseType, uriVariables).block());
    }

    /**
     * Performs a non-blocking GET request.
     *
     * @param uriTemplate  relative URI template
     * @param responseType expected response body type
     * @param uriVariables URI template variables
     * @param <R>          response type
     * @return a {@link Mono} emitting the response body or a translated error
     */
    protected <R> Mono<R> getAsync(String uriTemplate, Class<R> responseType, Object... uriVariables) {
        return retrieve(webClient.get().uri(uriTemplate, uriVariables).accept(MediaType.APPLICATION_JSON), responseType);
    }

    /**
     * Performs a non-blocking POST request with a JSON body.
     *
     * @param uriTemplate  relative URI template
     * @param body         request body
     * @param responseType expected response body type
     * @param uriVariables URI template variables
     * @param <B>          request body type
     * @param <R>          response type
     * @return a {@link Mono} emitting the response body or a translated error
     */
    protected <B, R> Mono<R> postAsync(String uriTemplate, B body, Class<R> responseType, Object... uriVariables) {
        return retrieve(webClient.post().uri(uriTemplate, uriVariables)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body), responseType);
    }

    /**
     * Returns the logical name of the downstream service.
     *
     * @return service name
     */
    protected String getServiceName() {
        return serviceName;
    }

    /**
     * Executes the request and maps HTTP and transport errors to application exceptions.
     *
     * @param spec         prepared request
     * @param responseType expected response body type
     * @param <R>          response type
     * @return a {@link Mono} emitting the response body or a translated error
     */
    private <R> Mono<R> retrieve(WebClient.RequestHeadersSpec<?> spec, Class<R> responseType) {
        Mono<R> response = spec.retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> Mono.error(toException(clientResponse.statusCode())))
                .bodyToMono(responseType);
        return translateErrors(response);
    }

    /**
     * Maps transport-level failures (connection refused, timeouts) to {@link ExternalServiceException}.
     *
     * @param response response publisher
     * @param <R>      response type
     * @return the publisher with translated errors
     */
    private <R> Mono<R> translateErrors(Mono<R> response) {
        return response.onErrorMap(WebClientRequestException.class, ex -> new ExternalServiceException(
                isTimeout(ex) ? ErrorCode.EXTERNAL_SERVICE_TIMEOUT : ErrorCode.EXTERNAL_SERVICE_ERROR, serviceName, ex));
    }

    /**
     * Converts an error HTTP status of the downstream service into an application exception.
     *
     * @param status downstream HTTP status
     * @return the exception to raise
     */
    private RuntimeException toException(HttpStatusCode status) {
        if (status.is4xxClientError()) {
            return new ExternalServiceClientException(serviceName, status.value());
        }
        ErrorCode errorCode = status.value() == 504 ? ErrorCode.EXTERNAL_SERVICE_TIMEOUT : ErrorCode.EXTERNAL_SERVICE_ERROR;
        return new ExternalServiceException(errorCode, serviceName, null);
    }

    /**
     * Ensures the downstream service returned a body.
     *
     * @param body response body
     * @param <R>  response type
     * @return the body
     * @throws ExternalServiceException when the body is empty
     */
    private <R> R requireBody(R body) {
        if (body == null) {
            throw new ExternalServiceException(ErrorCode.EXTERNAL_SERVICE_ERROR, serviceName, null);
        }
        return body;
    }

    /**
     * Checks whether a transport error was caused by a timeout.
     *
     * @param ex transport error
     * @return {@code true} for read/write/connect/response timeouts
     */
    private static boolean isTimeout(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.util.concurrent.TimeoutException
                    || cause instanceof io.netty.handler.timeout.TimeoutException
                    || cause instanceof io.netty.channel.ConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
