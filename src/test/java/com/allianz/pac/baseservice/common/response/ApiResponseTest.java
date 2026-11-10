package com.allianz.pac.baseservice.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.allianz.pac.baseservice.common.constants.AppConstants;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Unit tests for {@link ApiResponse}.
 */
class ApiResponseTest {

    /**
     * Clears the MDC after each test.
     */
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /**
     * Verifies the success envelope fields including the correlation id from the MDC.
     */
    @Test
    @DisplayName("success() fills all envelope fields")
    void successFillsEnvelope() {
        MDC.put(AppConstants.CORRELATION_ID_MDC_KEY, "corr-1");

        ApiResponse<String> response = ApiResponse.success(200, "ok", "payload");

        assertThat(response.status()).isEqualTo(ResponseStatus.SUCCESS);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.message()).isEqualTo("ok");
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.timestamp()).isNotNull();
        assertThat(response.correlationId()).isEqualTo("corr-1");
    }

    /**
     * Verifies that a missing payload becomes an empty object.
     */
    @Test
    @DisplayName("success() replaces null data with an empty object")
    void successWithNullData() {
        ApiResponse<Object> response = ApiResponse.success(200, "ok", null);

        assertThat(response.data()).isEqualTo(Map.of());
    }

    /**
     * Verifies the failure envelope with and without details.
     */
    @Test
    @DisplayName("failure() fills status FAILURE and never returns null data")
    void failureFillsEnvelope() {
        ApiResponse<Map<String, String>> withDetails = ApiResponse.failure(400, "invalid", Map.of("email", "bad"));
        ApiResponse<Map<String, String>> withoutDetails = ApiResponse.failure(500, "error", null);

        assertThat(withDetails.status()).isEqualTo(ResponseStatus.FAILURE);
        assertThat(withDetails.statusCode()).isEqualTo(400);
        assertThat(withDetails.data()).containsEntry("email", "bad");
        assertThat(withoutDetails.data()).isEmpty();
    }
}
