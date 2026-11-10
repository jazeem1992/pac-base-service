package com.allianz.pac.baseservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.allianz.pac.baseservice.exception.ExternalServiceClientException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Verifies that the declarative Resilience4j annotations on {@link CreditScoreClient} are active
 * in the real application context: transient failures are retried, client errors are not.
 * The downstream HTTP service is simulated by a stubbed WebClient.
 */
@SpringBootTest
@ActiveProfiles("test")
class CreditScoreClientResilienceIT {

    /** Number of HTTP calls the stub has received. */
    private static final AtomicInteger CALLS = new AtomicInteger();

    /** Number of initial calls answered with HTTP 503 before succeeding. */
    private static final AtomicInteger FAILURES_BEFORE_SUCCESS = new AtomicInteger();

    /** Status returned for failing calls. */
    private static volatile HttpStatus failureStatus = HttpStatus.SERVICE_UNAVAILABLE;

    @TestBean(name = "creditScoreWebClient", methodName = "stubWebClient")
    private WebClient creditScoreWebClient;

    @Autowired
    private CreditScoreClient creditScoreClient;

    /**
     * Replaces the real WebClient: fails the configured number of times, then returns a score.
     *
     * @return the stubbed WebClient
     */
    static WebClient stubWebClient() {
        return WebClient.builder()
                .baseUrl("http://credit-score-service")
                .exchangeFunction(request -> {
                    int call = CALLS.incrementAndGet();
                    if (call <= FAILURES_BEFORE_SUCCESS.get()) {
                        return Mono.just(ClientResponse.create(failureStatus).build());
                    }
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"customerId\":1,\"score\":650,\"rating\":\"B\"}")
                            .build());
                })
                .build();
    }

    /**
     * Resets the stub before each test.
     */
    @BeforeEach
    void resetStub() {
        CALLS.set(0);
        FAILURES_BEFORE_SUCCESS.set(0);
        failureStatus = HttpStatus.SERVICE_UNAVAILABLE;
    }

    /**
     * Verifies that two transient failures are retried and the third attempt succeeds.
     */
    @Test
    @DisplayName("@Retry retries transient 5xx failures")
    void retriesTransientFailures() {
        FAILURES_BEFORE_SUCCESS.set(2);

        assertThat(creditScoreClient.fetchCreditScore(1L).score()).isEqualTo(650);
        assertThat(CALLS.get()).isEqualTo(3);
    }

    /**
     * Verifies that 4xx client errors are not retried.
     */
    @Test
    @DisplayName("@Retry does not retry 4xx client errors")
    void doesNotRetryClientErrors() {
        FAILURES_BEFORE_SUCCESS.set(5);
        failureStatus = HttpStatus.BAD_REQUEST;

        assertThatThrownBy(() -> creditScoreClient.fetchCreditScore(1L)).isInstanceOf(ExternalServiceClientException.class);
        assertThat(CALLS.get()).isEqualTo(1);
    }
}
