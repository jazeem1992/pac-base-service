package com.allianz.pac.baseservice.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.slf4j.event.Level;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Logging behaviour of the service ({@code app.logging.*}).
 *
 * @param correlationIdHeader    HTTP header used to receive/propagate the correlation id
 * @param methodLogLevel         level used for method START/END logs written by the logging aspect
 * @param logArguments           whether method arguments/results are logged (at DEBUG level)
 * @param maxArgumentLength      maximum length of a logged argument/result before truncation
 * @param slowExecutionThreshold methods slower than this are logged at WARN
 * @param maskedFields           field names whose values are masked in logs (PII, secrets)
 * @param excludedPaths          request path prefixes excluded from request START/END logs
 */
@Validated
@ConfigurationProperties(prefix = "app.logging")
public record LoggingProperties(
        @NotBlank String correlationIdHeader,
        @NotNull Level methodLogLevel,
        boolean logArguments,
        @Min(16) int maxArgumentLength,
        @NotNull Duration slowExecutionThreshold,
        List<String> maskedFields,
        List<String> excludedPaths) {

    /**
     * Normalises optional lists so callers never deal with {@code null}.
     */
    public LoggingProperties {
        maskedFields = maskedFields == null ? List.of() : List.copyOf(maskedFields);
        excludedPaths = excludedPaths == null ? List.of() : List.copyOf(excludedPaths);
    }
}
