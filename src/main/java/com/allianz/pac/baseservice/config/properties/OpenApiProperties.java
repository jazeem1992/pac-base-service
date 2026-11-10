package com.allianz.pac.baseservice.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * OpenAPI documentation metadata ({@code app.openapi.*}).
 *
 * @param title        API title
 * @param description  API description
 * @param version      API version
 * @param contactName  owning team
 * @param contactEmail owning team e-mail
 */
@Validated
@ConfigurationProperties(prefix = "app.openapi")
public record OpenApiProperties(
        @NotBlank String title,
        @NotBlank String description,
        @NotBlank String version,
        String contactName,
        String contactEmail) {
}
