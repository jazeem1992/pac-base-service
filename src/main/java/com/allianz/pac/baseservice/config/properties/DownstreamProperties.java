package com.allianz.pac.baseservice.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Endpoints of downstream services called through WebClient ({@code app.downstream.services.*}).
 *
 * <p>With Consul discovery the base URL uses the logical service name, e.g.
 * {@code http://credit-score-service}; the load-balanced WebClient resolves it to a healthy instance.
 *
 * @param services downstream services keyed by a logical key (e.g. {@code credit-score})
 */
@Validated
@ConfigurationProperties(prefix = "app.downstream")
public record DownstreamProperties(Map<String, @Valid ServiceEndpoint> services) {

    /**
     * Normalises the optional map so callers never deal with {@code null}.
     */
    public DownstreamProperties {
        services = services == null ? Map.of() : Map.copyOf(services);
    }

    /**
     * Returns the configuration of a downstream service, failing fast when it is missing.
     *
     * @param key logical key of the service
     * @return the endpoint configuration
     * @throws IllegalStateException when the service is not configured
     */
    public ServiceEndpoint getRequired(String key) {
        ServiceEndpoint endpoint = services.get(key);
        if (endpoint == null) {
            throw new IllegalStateException("Missing configuration 'app.downstream.services." + key + "'");
        }
        return endpoint;
    }

    /**
     * Endpoint configuration of one downstream service.
     *
     * @param serviceName  logical service name (used in logs, errors and Consul lookups)
     * @param baseUrl      base URL of the service
     * @param loadBalanced {@code true} to resolve the host of {@code baseUrl} via Consul discovery
     * @param paths        relative paths of the operations, keyed by operation name
     */
    public record ServiceEndpoint(
            @NotBlank String serviceName,
            @NotBlank String baseUrl,
            boolean loadBalanced,
            Map<String, String> paths) {

        /**
         * Normalises the optional map so callers never deal with {@code null}.
         */
        public ServiceEndpoint {
            paths = paths == null ? Map.of() : Map.copyOf(paths);
        }

        /**
         * Returns a configured operation path, failing fast when it is missing.
         *
         * @param operation operation name
         * @return the relative path (may contain URI template variables)
         * @throws IllegalStateException when the path is not configured
         */
        public String path(String operation) {
            String path = paths.get(operation);
            if (path == null) {
                throw new IllegalStateException("Missing path '" + operation + "' for service '" + serviceName + "'");
            }
            return path;
        }
    }
}
