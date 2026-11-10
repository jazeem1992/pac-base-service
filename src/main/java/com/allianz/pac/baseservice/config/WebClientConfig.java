package com.allianz.pac.baseservice.config;

import com.allianz.pac.baseservice.client.WebClientFilters;
import com.allianz.pac.baseservice.config.properties.DownstreamProperties;
import com.allianz.pac.baseservice.config.properties.DownstreamProperties.ServiceEndpoint;
import com.allianz.pac.baseservice.config.properties.LoggingProperties;
import com.allianz.pac.baseservice.config.properties.WebClientProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancedExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * Configures the outbound HTTP stack used to call other microservices.
 *
 * <ul>
 *   <li>one shared, pooled Reactor Netty connector tuned by {@code app.web-client.*} (timeouts,
 *       pool size) - built for high request volumes,</li>
 *   <li>correlation id propagation and outbound request logging on every call,</li>
 *   <li>Consul-based client-side load balancing for endpoints with {@code load-balanced: true},</li>
 *   <li>one {@link WebClient} bean per downstream service.</li>
 * </ul>
 * The Spring Boot {@link WebClient.Builder} is reused, so JSON codecs share the application
 * JsonMapper (unknown properties are ignored, see {@code spring.jackson.*}).
 */
@Configuration(proxyBeanMethods = false)
public class WebClientConfig {

    /** Key of the credit-score service under {@code app.downstream.services}. */
    public static final String CREDIT_SCORE_SERVICE_KEY = "credit-score";

    /**
     * Creates the pooled Reactor Netty connector shared by all WebClients.
     *
     * @param properties WebClient tuning properties
     * @return the HTTP connector
     */
    @Bean
    public ClientHttpConnector pooledClientHttpConnector(WebClientProperties properties) {
        WebClientProperties.Pool pool = properties.pool();
        ConnectionProvider connectionProvider = ConnectionProvider.builder(pool.name())
                .maxConnections(pool.maxConnections())
                .pendingAcquireMaxCount(pool.pendingAcquireMaxCount())
                .pendingAcquireTimeout(pool.pendingAcquireTimeout())
                .maxIdleTime(pool.maxIdleTime())
                .maxLifeTime(pool.maxLifeTime())
                .evictInBackground(pool.evictInBackground())
                .metrics(true)
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(properties.connectTimeout().toMillis()))
                .responseTimeout(properties.responseTimeout())
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(properties.readTimeout().toMillis(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(properties.writeTimeout().toMillis(), TimeUnit.MILLISECONDS)));
        return new ReactorClientHttpConnector(httpClient);
    }

    /**
     * Creates the WebClient used to call the credit-score service.
     *
     * @param builder             Spring Boot's preconfigured WebClient builder (prototype)
     * @param connector           shared pooled connector
     * @param webClientProperties WebClient tuning properties
     * @param loggingProperties   logging properties (correlation id header)
     * @param downstream          downstream endpoint configuration
     * @param loadBalancer        Consul load-balancer filter, if available
     * @return the configured WebClient
     */
    @Bean
    public WebClient creditScoreWebClient(WebClient.Builder builder,
                                          ClientHttpConnector connector,
                                          WebClientProperties webClientProperties,
                                          LoggingProperties loggingProperties,
                                          DownstreamProperties downstream,
                                          ObjectProvider<LoadBalancedExchangeFilterFunction> loadBalancer) {
        ServiceEndpoint endpoint = downstream.getRequired(CREDIT_SCORE_SERVICE_KEY);
        return buildWebClient(builder, connector, webClientProperties, loggingProperties, endpoint, loadBalancer);
    }

    /**
     * Builds a WebClient for one downstream endpoint. Reuse this method for every new downstream
     * service bean.
     *
     * @param builder             Spring Boot's preconfigured WebClient builder
     * @param connector           shared pooled connector
     * @param webClientProperties WebClient tuning properties
     * @param loggingProperties   logging properties (correlation id header)
     * @param endpoint            endpoint configuration of the downstream service
     * @param loadBalancer        Consul load-balancer filter, if available
     * @return the configured WebClient
     */
    static WebClient buildWebClient(WebClient.Builder builder,
                                    ClientHttpConnector connector,
                                    WebClientProperties webClientProperties,
                                    LoggingProperties loggingProperties,
                                    ServiceEndpoint endpoint,
                                    ObjectProvider<LoadBalancedExchangeFilterFunction> loadBalancer) {
        WebClient.Builder configured = builder.clone()
                .baseUrl(endpoint.baseUrl())
                .clientConnector(connector)
                .codecs(codecs -> codecs.defaultCodecs()
                        .maxInMemorySize(Math.toIntExact(webClientProperties.maxInMemorySize().toBytes())))
                .filter(WebClientFilters.correlationIdPropagation(loggingProperties.correlationIdHeader()))
                .filter(WebClientFilters.outboundLogging(endpoint.serviceName()));
        if (endpoint.loadBalanced()) {
            loadBalancer.ifAvailable(configured::filter);
        }
        return configured.build();
    }
}
