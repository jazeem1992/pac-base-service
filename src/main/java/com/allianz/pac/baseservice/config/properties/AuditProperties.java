package com.allianz.pac.baseservice.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JPA auditing settings ({@code app.audit.*}).
 *
 * @param defaultAuditor auditor written to {@code created_by}/{@code updated_by} when no
 *                       authenticated user is available (replace with the security principal once
 *                       authentication is added)
 */
@Validated
@ConfigurationProperties(prefix = "app.audit")
public record AuditProperties(@NotBlank String defaultAuditor) {
}
