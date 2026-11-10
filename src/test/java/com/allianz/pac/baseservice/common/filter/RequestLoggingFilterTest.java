package com.allianz.pac.baseservice.common.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.allianz.pac.baseservice.common.constants.AppConstants;
import com.allianz.pac.baseservice.support.TestFixtures;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link RequestLoggingFilter}.
 */
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter(TestFixtures.loggingProperties(Duration.ofSeconds(1)));

    /**
     * Verifies that a normal request is processed and its start time is recorded.
     *
     * @throws Exception when the filter fails
     */
    @Test
    @DisplayName("records the request start time and continues the chain")
    void logsRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/customers");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(request.getAttribute(AppConstants.REQUEST_START_TIME_ATTRIBUTE)).isInstanceOf(Long.class);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    /**
     * Verifies that excluded paths are passed through without logging.
     *
     * @throws Exception when the filter fails
     */
    @Test
    @DisplayName("skips excluded paths")
    void skipsExcludedPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(request.getAttribute(AppConstants.REQUEST_START_TIME_ATTRIBUTE)).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
