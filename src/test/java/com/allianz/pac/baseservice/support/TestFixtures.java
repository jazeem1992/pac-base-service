package com.allianz.pac.baseservice.support;

import com.allianz.pac.baseservice.config.properties.LoggingProperties;
import com.allianz.pac.baseservice.dto.request.CustomerRequest;
import com.allianz.pac.baseservice.dto.response.CustomerResponse;
import com.allianz.pac.baseservice.model.CustomerStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.event.Level;

/**
 * Shared test data builders.
 */
public final class TestFixtures {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";

    /**
     * Prevents instantiation of this utility class.
     */
    private TestFixtures() {
        throw new UnsupportedOperationException("Utility class must not be instantiated");
    }

    /**
     * Creates a valid customer request.
     *
     * @param email e-mail of the customer
     * @return the request
     */
    public static CustomerRequest customerRequest(String email) {
        return new CustomerRequest("John", "Doe", email, "+49 123456789", CustomerStatus.ACTIVE);
    }

    /**
     * Creates a customer response.
     *
     * @param id    customer id
     * @param email e-mail of the customer
     * @return the response
     */
    public static CustomerResponse customerResponse(Long id, String email) {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return new CustomerResponse(id, "John", "Doe", email, "+49 123456789", CustomerStatus.ACTIVE, now, now);
    }

    /**
     * Creates logging properties suitable for unit tests.
     *
     * @param slowThreshold slow execution threshold
     * @return the properties
     */
    public static LoggingProperties loggingProperties(Duration slowThreshold) {
        return new LoggingProperties(CORRELATION_HEADER, Level.INFO, true, 200, slowThreshold,
                List.of("email"), List.of("/actuator"));
    }
}
