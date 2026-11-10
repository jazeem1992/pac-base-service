package com.allianz.pac.baseservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.allianz.pac.baseservice.config.properties.DownstreamProperties;
import com.allianz.pac.baseservice.config.properties.DownstreamProperties.ServiceEndpoint;
import com.allianz.pac.baseservice.dto.response.CreditScoreResponse;
import com.allianz.pac.baseservice.exception.ErrorCode;
import com.allianz.pac.baseservice.exception.ExternalServiceClientException;
import com.allianz.pac.baseservice.exception.ExternalServiceException;
import java.net.ConnectException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests for {@link CreditScoreClient} / {@link BaseWebClient} using stubbed HTTP exchanges
 * (no network). Resilience annotations are verified in {@code CreditScoreClientResilienceIT}.
 */
class CreditScoreClientTest {

    private static final DownstreamProperties DOWNSTREAM = new DownstreamProperties(Map.of("credit-score",
            new ServiceEndpoint("credit-score-service", "http://credit-score-service", true,
                    Map.of("get-score", "/api/v1/credit-scores/{customerId}"))));

    /**
     * Creates a client whose HTTP exchanges are answered by the given function.
     *
     * @param exchangeFunction stubbed exchange
     * @return the client under test
     */
    private static CreditScoreClient client(ExchangeFunction exchangeFunction) {
        JsonMapper lenientMapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        WebClient webClient = WebClient.builder()
                .baseUrl("http://credit-score-service")
                .exchangeFunction(exchangeFunction)
                .codecs(codecs -> codecs.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(lenientMapper)))
                .build();
        return new CreditScoreClient(webClient, DOWNSTREAM);
    }

    /**
     * Creates a stubbed JSON response.
     *
     * @param status HTTP status
     * @param body   response body
     * @return the client response
     */
    private static Mono<ClientResponse> jsonResponse(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    /**
     * Verifies a successful call, the request path and that unknown JSON fields are ignored.
     */
    @Test
    @DisplayName("returns the credit score and ignores unknown fields")
    void returnsScore() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        CreditScoreClient client = client(request -> {
            captured.set(request);
            return jsonResponse(HttpStatus.OK, "{\"customerId\":5,\"score\":720,\"rating\":\"A\",\"unknownField\":\"x\"}");
        });

        CreditScoreResponse response = client.fetchCreditScore(5L);

        assertThat(response.score()).isEqualTo(720);
        assertThat(response.rating()).isEqualTo("A");
        assertThat(captured.get().url().getPath()).isEqualTo("/api/v1/credit-scores/5");
        assertThat(captured.get().method()).isEqualTo(HttpMethod.GET);
    }

    /**
     * Verifies that 4xx responses are not retryable client errors.
     */
    @Test
    @DisplayName("4xx -> ExternalServiceClientException")
    void clientError() {
        CreditScoreClient client = client(request -> jsonResponse(HttpStatus.NOT_FOUND, "{}"));

        assertThatThrownBy(() -> client.fetchCreditScore(1L))
                .isInstanceOfSatisfying(ExternalServiceClientException.class,
                        ex -> assertThat(ex.getDownstreamStatus()).isEqualTo(404));
    }

    /**
     * Verifies that 5xx responses are transient server errors.
     */
    @Test
    @DisplayName("5xx -> ExternalServiceException(EXTERNAL_SERVICE_ERROR)")
    void serverError() {
        CreditScoreClient client = client(request -> jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR, "{}"));

        assertThatThrownBy(() -> client.fetchCreditScore(1L))
                .isInstanceOfSatisfying(ExternalServiceException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR));
    }

    /**
     * Verifies that a downstream 504 is reported as timeout.
     */
    @Test
    @DisplayName("504 -> ExternalServiceException(EXTERNAL_SERVICE_TIMEOUT)")
    void gatewayTimeout() {
        CreditScoreClient client = client(request -> jsonResponse(HttpStatus.GATEWAY_TIMEOUT, "{}"));

        assertThatThrownBy(() -> client.fetchCreditScore(1L))
                .isInstanceOfSatisfying(ExternalServiceException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_TIMEOUT));
    }

    /**
     * Verifies that connection errors are transient server errors.
     */
    @Test
    @DisplayName("connection error -> ExternalServiceException(EXTERNAL_SERVICE_ERROR)")
    void connectionError() {
        CreditScoreClient client = client(request -> Mono.error(requestException(new ConnectException("refused"))));

        assertThatThrownBy(() -> client.fetchCreditScore(1L))
                .isInstanceOfSatisfying(ExternalServiceException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_ERROR));
    }

    /**
     * Verifies that transport timeouts are reported as timeout.
     */
    @Test
    @DisplayName("transport timeout -> ExternalServiceException(EXTERNAL_SERVICE_TIMEOUT)")
    void transportTimeout() {
        CreditScoreClient client = client(request -> Mono.error(requestException(new TimeoutException("slow"))));

        assertThatThrownBy(() -> client.fetchCreditScore(1L))
                .isInstanceOfSatisfying(ExternalServiceException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_SERVICE_TIMEOUT));
    }

    /**
     * Verifies that an empty body is treated as a failure.
     */
    @Test
    @DisplayName("empty body -> ExternalServiceException")
    void emptyBody() {
        CreditScoreClient client = client(request -> Mono.just(ClientResponse.create(HttpStatus.OK).build()));

        assertThatThrownBy(() -> client.fetchCreditScore(1L)).isInstanceOf(ExternalServiceException.class);
    }

    /**
     * Creates a WebClient transport exception wrapping the given cause.
     *
     * @param cause transport failure
     * @return the request exception
     */
    private static WebClientRequestException requestException(Throwable cause) {
        return new WebClientRequestException(cause, HttpMethod.GET, URI.create("http://credit-score-service"), new HttpHeaders());
    }
}
