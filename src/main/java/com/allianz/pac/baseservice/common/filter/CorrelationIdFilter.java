package com.allianz.pac.baseservice.common.filter;

import com.allianz.pac.baseservice.common.constants.AppConstants;
import com.allianz.pac.baseservice.config.properties.LoggingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a correlation id to every request.
 *
 * <p>The id is taken from the configured request header (when present and well-formed) or
 * generated, stored in the logging MDC, returned in the response header and propagated to
 * downstream calls by the WebClient. It is kept for async and error dispatches of the same request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private final LoggingProperties loggingProperties;

    /**
     * Resolves the correlation id, exposes it via MDC and response header, and clears the MDC once
     * the request has been processed.
     *
     * @param request     current request
     * @param response    current response
     * @param filterChain remaining filter chain
     * @throws ServletException when a downstream filter fails
     * @throws IOException      when an I/O error occurs
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        MDC.put(AppConstants.CORRELATION_ID_MDC_KEY, correlationId);
        response.setHeader(loggingProperties.correlationIdHeader(), correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(AppConstants.CORRELATION_ID_MDC_KEY);
        }
    }

    /**
     * Restores the correlation id for the nested error dispatch (handled by the error controller).
     *
     * @param request     current request
     * @param response    current response
     * @param filterChain remaining filter chain
     * @throws ServletException when a downstream filter fails
     * @throws IOException      when an I/O error occurs
     */
    @Override
    protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        doFilterInternal(request, response, filterChain);
    }

    /**
     * Ensures the filter also runs for async dispatches so the MDC is restored on the new thread.
     *
     * @return always {@code false}
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    /**
     * Ensures the filter also runs for error dispatches so error logs carry the correlation id.
     *
     * @return always {@code false}
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    /**
     * Returns the correlation id already assigned to this request, the valid incoming header value,
     * or a newly generated UUID - in that order.
     *
     * @param request current request
     * @return the correlation id
     */
    private String resolveCorrelationId(HttpServletRequest request) {
        Object existing = request.getAttribute(AppConstants.CORRELATION_ID_ATTRIBUTE);
        if (existing instanceof String id) {
            return id;
        }
        String incoming = request.getHeader(loggingProperties.correlationIdHeader());
        String correlationId = incoming != null && VALID_CORRELATION_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
        request.setAttribute(AppConstants.CORRELATION_ID_ATTRIBUTE, correlationId);
        return correlationId;
    }
}
