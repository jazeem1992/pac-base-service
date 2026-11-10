package com.allianz.pac.baseservice.common.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.allianz.pac.baseservice.common.constants.AppConstants;
import com.allianz.pac.baseservice.support.TestFixtures;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link CorrelationIdFilter}.
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter(TestFixtures.loggingProperties(Duration.ofSeconds(1)));

    /**
     * Verifies that a valid incoming correlation id is reused, exposed in the MDC during the
     * request and returned in the response header.
     *
     * @throws Exception when the filter fails
     */
    @Test
    @DisplayName("reuses a valid incoming correlation id")
    void reusesIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/customers");
        request.addHeader(TestFixtures.CORRELATION_HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcValue = new AtomicReference<>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                mdcValue.set(MDC.get(AppConstants.CORRELATION_ID_MDC_KEY));
            }
        });

        assertThat(mdcValue.get()).isEqualTo("abc-123");
        assertThat(response.getHeader(TestFixtures.CORRELATION_HEADER)).isEqualTo("abc-123");
        assertThat(MDC.get(AppConstants.CORRELATION_ID_MDC_KEY)).isNull();
    }

    /**
     * Verifies that an invalid incoming id (possible log injection) is replaced by a UUID.
     *
     * @throws Exception when the filter fails
     */
    @Test
    @DisplayName("generates a new id when the incoming one is invalid")
    void replacesInvalidId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/customers");
        request.addHeader(TestFixtures.CORRELATION_HEADER, "bad\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(TestFixtures.CORRELATION_HEADER))
                .isNotEqualTo("bad\nvalue")
                .matches("[0-9a-f-]{36}");
    }

    /**
     * Verifies that the id stored on the request is reused for later dispatches.
     *
     * @throws Exception when the filter fails
     */
    @Test
    @DisplayName("reuses the id already assigned to the request")
    void reusesRequestAttribute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.setAttribute(AppConstants.CORRELATION_ID_ATTRIBUTE, "existing");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(TestFixtures.CORRELATION_HEADER)).isEqualTo("existing");
    }
}
