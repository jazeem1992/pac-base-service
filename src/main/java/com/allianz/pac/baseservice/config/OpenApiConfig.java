package com.allianz.pac.baseservice.config;

import com.allianz.pac.baseservice.config.properties.OpenApiProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) metadata. Availability of the UI per environment is controlled by
 * {@code springdoc.api-docs.enabled} / {@code springdoc.swagger-ui.enabled}.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /**
     * Creates the OpenAPI document metadata from {@code app.openapi.*}.
     *
     * @param properties OpenAPI properties
     * @return the OpenAPI definition
     */
    @Bean
    public OpenAPI serviceOpenApi(OpenApiProperties properties) {
        return new OpenAPI().info(new Info()
                .title(properties.title())
                .description(properties.description())
                .version(properties.version())
                .contact(new Contact().name(properties.contactName()).email(properties.contactEmail())));
    }
}
