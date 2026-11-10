package com.allianz.pac.baseservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the PAC base microservice.
 *
 * <p>Every PAC microservice is created from this template. Configuration properties under
 * {@code com.allianz.pac.baseservice.config.properties} are discovered automatically.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BaseServiceApplication {

    /**
     * Bootstraps the Spring application context and starts the embedded web server.
     *
     * @param args command line arguments forwarded to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(BaseServiceApplication.class, args);
    }
}
