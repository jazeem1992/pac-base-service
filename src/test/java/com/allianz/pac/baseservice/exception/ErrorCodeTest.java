package com.allianz.pac.baseservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link ErrorCode}.
 */
class ErrorCodeTest {

    /**
     * Verifies the mapping of raw HTTP statuses to error codes.
     *
     * @param status   HTTP status
     * @param expected expected error code
     */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "400, MALFORMED_REQUEST",
        "404, ENDPOINT_NOT_FOUND",
        "405, METHOD_NOT_ALLOWED",
        "406, NOT_ACCEPTABLE",
        "415, UNSUPPORTED_MEDIA_TYPE",
        "429, TOO_MANY_REQUESTS",
        "503, SERVICE_UNAVAILABLE",
        "500, INTERNAL_ERROR",
        "418, INTERNAL_ERROR"
    })
    void mapsHttpStatus(int status, ErrorCode expected) {
        assertThat(ErrorCode.fromHttpStatus(status)).isEqualTo(expected);
    }
}
