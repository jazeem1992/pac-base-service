package com.allianz.pac.baseservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.allianz.pac.baseservice.common.constants.AppConstants;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

/**
 * Unit tests for {@link WebClientFilters}.
 */
class WebClientFiltersTest {

    private static final String HEADER = "X-Correlation-Id";

    /**
     * Clears the MDC after each test.
     */
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /**
     * Verifies that the correlation id of the MDC is added to outbound requests.
     */
    @Test
    @DisplayName("adds the correlation id header from the MDC")
    void propagatesCorrelationId() {
        MDC.put(AppConstants.CORRELATION_ID_MDC_KEY, "corr-42");
        AtomicReference<ClientRequest> sent = new AtomicReference<>();
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("http://svc/a")).build();

        WebClientFilters.correlationIdPropagation(HEADER)
                .filter(request, req -> {
                    sent.set(req);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
                .block();

        assertThat(sent.get().headers().getFirst(HEADER)).isEqualTo("corr-42");
    }

    /**
     * Verifies that requests are sent unchanged when no correlation id is available.
     */
    @Test
    @DisplayName("leaves the request unchanged without correlation id")
    void noCorrelationId() {
        AtomicReference<ClientRequest> sent = new AtomicReference<>();
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("http://svc/a")).build();

        WebClientFilters.correlationIdPropagation(HEADER)
                .filter(request, req -> {
                    sent.set(req);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
                .block();

        assertThat(sent.get()).isSameAs(request);
    }

    /**
     * Verifies that the logging filter passes responses and errors through.
     */
    @Test
    @DisplayName("outbound logging passes responses and errors through")
    void outboundLogging() {
        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("http://svc/a")).build();

        ClientResponse response = WebClientFilters.outboundLogging("svc")
                .filter(request, req -> Mono.just(ClientResponse.create(HttpStatus.OK).build()))
                .block();
        Mono<ClientResponse> failing = WebClientFilters.outboundLogging("svc")
                .filter(request, req -> Mono.error(new IllegalStateException("down")));

        assertThat(response).isNotNull();
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
        assertThatThrownBy(failing::block).isInstanceOf(IllegalStateException.class);
    }
}
