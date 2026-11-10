package com.allianz.pac.baseservice.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LogSanitizer}.
 */
class LogSanitizerTest {

    /**
     * Verifies that line breaks are replaced to prevent log forging.
     */
    @Test
    @DisplayName("clean() removes CR/LF characters")
    void cleanRemovesLineBreaks() {
        assertThat(LogSanitizer.clean("line1\r\nline2")).isEqualTo("line1__line2");
    }

    /**
     * Verifies that {@code null} is rendered as the text {@code null}.
     */
    @Test
    @DisplayName("clean() renders null safely")
    void cleanHandlesNull() {
        assertThat(LogSanitizer.clean(null)).isEqualTo("null");
    }

    /**
     * Verifies masking of {@code field=value} pairs (record toString format).
     */
    @Test
    @DisplayName("sanitize() masks configured fields in toString output")
    void sanitizeMasksRecordFields() {
        String result = LogSanitizer.sanitize("Request[name=John, email=john@test.com, password=secret]",
                List.of("email", "password"), 500);

        assertThat(result).isEqualTo("Request[name=John, email=****, password=****]");
    }

    /**
     * Verifies masking of JSON {@code "field":"value"} pairs.
     */
    @Test
    @DisplayName("sanitize() masks configured fields in JSON")
    void sanitizeMasksJsonFields() {
        String result = LogSanitizer.sanitize("{\"email\":\"john@test.com\",\"name\":\"John\"}", List.of("email"), 500);

        assertThat(result).isEqualTo("{\"email\":\"****\",\"name\":\"John\"}");
    }

    /**
     * Verifies that long values are truncated.
     */
    @Test
    @DisplayName("sanitize() truncates values longer than the limit")
    void sanitizeTruncates() {
        String result = LogSanitizer.sanitize("x".repeat(50), List.of(), 20);

        assertThat(result).startsWith("x".repeat(20)).endsWith("...(truncated)");
    }

    /**
     * Verifies that values are untouched when no mask fields are configured.
     */
    @Test
    @DisplayName("sanitize() leaves values unchanged without masked fields")
    void sanitizeWithoutMaskedFields() {
        assertThat(LogSanitizer.sanitize("email=a@b.c", null, 100)).isEqualTo("email=a@b.c");
    }
}
