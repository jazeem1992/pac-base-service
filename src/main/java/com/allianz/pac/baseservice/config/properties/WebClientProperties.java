package com.allianz.pac.baseservice.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Tuning of the shared WebClient / Reactor Netty HTTP client ({@code app.web-client.*}).
 *
 * @param connectTimeout   TCP connect timeout
 * @param responseTimeout  maximum time to wait for a complete response
 * @param readTimeout      maximum inactivity while reading
 * @param writeTimeout     maximum inactivity while writing
 * @param maxInMemorySize  maximum response body buffered in memory
 * @param pool             connection pool settings
 */
@Validated
@ConfigurationProperties(prefix = "app.web-client")
public record WebClientProperties(
        @NotNull Duration connectTimeout,
        @NotNull Duration responseTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration writeTimeout,
        @NotNull DataSize maxInMemorySize,
        @NotNull @Valid Pool pool) {

    /**
     * Connection pool settings; sized for high request volumes.
     *
     * @param name                   pool name (visible in metrics)
     * @param maxConnections         maximum open connections across all hosts
     * @param pendingAcquireMaxCount maximum requests queued while waiting for a connection
     * @param pendingAcquireTimeout  maximum time to wait for a free connection
     * @param maxIdleTime            idle connections are closed after this time
     * @param maxLifeTime            connections are recycled after this time
     * @param evictInBackground      interval of background eviction of idle/expired connections
     */
    public record Pool(
            @NotNull String name,
            @Min(1) int maxConnections,
            @Min(1) int pendingAcquireMaxCount,
            @NotNull Duration pendingAcquireTimeout,
            @NotNull Duration maxIdleTime,
            @NotNull Duration maxLifeTime,
            @NotNull Duration evictInBackground) {
    }
}
