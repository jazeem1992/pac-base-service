package com.allianz.pac.baseservice.config;

import com.allianz.pac.baseservice.config.properties.AuditProperties;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing so {@code created_at/by} and {@code updated_at/by} are filled automatically.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    /**
     * Provides the current auditor. Replace with the authenticated principal once security is added.
     *
     * @param properties audit properties
     * @return the auditor provider
     */
    @Bean
    public AuditorAware<String> auditorProvider(AuditProperties properties) {
        return () -> Optional.of(properties.defaultAuditor());
    }

    /**
     * Provides audit timestamps as UTC {@link Instant}s.
     *
     * @return the date-time provider
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(Instant.now());
    }
}
