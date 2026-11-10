package com.allianz.pac.baseservice.client;

import com.allianz.pac.baseservice.common.constants.ResilienceInstances;
import com.allianz.pac.baseservice.config.WebClientConfig;
import com.allianz.pac.baseservice.config.properties.DownstreamProperties;
import com.allianz.pac.baseservice.config.properties.DownstreamProperties.ServiceEndpoint;
import com.allianz.pac.baseservice.dto.response.CreditScoreResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Sample downstream client: reads credit scores from the {@code credit-score-service}
 * (discovered through Consul).
 *
 * <p>Resilience is applied declaratively and configured per instance in the property files
 * ({@code resilience4j.*.instances.creditScoreService}):
 * <ul>
 *   <li>{@code @Retry} - retries transient failures with exponential back-off,</li>
 *   <li>{@code @CircuitBreaker} - stops calling a failing service and fails fast,</li>
 *   <li>{@code @Bulkhead} - limits concurrent calls so a slow service cannot exhaust resources.</li>
 * </ul>
 */
@Component
public class CreditScoreClient extends BaseWebClient {

    private static final String GET_SCORE_OPERATION = "get-score";

    private final String scorePath;

    /**
     * Creates the client.
     *
     * @param webClient  WebClient configured for the credit-score service
     * @param downstream downstream endpoint configuration
     */
    public CreditScoreClient(@Qualifier("creditScoreWebClient") WebClient webClient, DownstreamProperties downstream) {
        super(webClient, endpoint(downstream).serviceName());
        this.scorePath = endpoint(downstream).path(GET_SCORE_OPERATION);
    }

    /**
     * Resolves the credit-score endpoint configuration.
     *
     * @param downstream downstream endpoint configuration
     * @return the credit-score endpoint
     */
    private static ServiceEndpoint endpoint(DownstreamProperties downstream) {
        return downstream.getRequired(WebClientConfig.CREDIT_SCORE_SERVICE_KEY);
    }

    /**
     * Fetches the credit score of a customer.
     *
     * @param customerId customer id
     * @return the credit score
     */
    @Retry(name = ResilienceInstances.CREDIT_SCORE_SERVICE)
    @CircuitBreaker(name = ResilienceInstances.CREDIT_SCORE_SERVICE)
    @Bulkhead(name = ResilienceInstances.CREDIT_SCORE_SERVICE)
    public CreditScoreResponse fetchCreditScore(Long customerId) {
        return get(scorePath, CreditScoreResponse.class, customerId);
    }
}
