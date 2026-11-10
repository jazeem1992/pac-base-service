package com.allianz.pac.baseservice.common.filter;

import com.allianz.pac.baseservice.common.constants.AppConstants;
import com.allianz.pac.baseservice.config.properties.LoggingProperties;
import com.allianz.pac.baseservice.util.LogSanitizer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs the START and END of every HTTP request with method, path, status and duration.
 *
 * <p>For asynchronous requests the END line is written when the async processing completes.
 * Paths listed in {@code app.logging.excluded-paths} (e.g. health checks) are not logged. Query
 * strings are not logged because they may contain personal data.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter {

    private final LoggingProperties loggingProperties;

    /**
     * Writes the request START line, executes the request and writes the END line.
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
        String method = request.getMethod();
        String path = LogSanitizer.clean(request.getRequestURI());
        if (!isAsyncDispatch(request)) {
            request.setAttribute(AppConstants.REQUEST_START_TIME_ATTRIBUTE, System.nanoTime());
            log.info("REQUEST START {} {}", method, path);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (isAsyncStarted(request)) {
                log.debug("REQUEST ASYNC PROCESSING {} {}", method, path);
            } else {
                log.info("REQUEST END {} {} - status {} - {} ms", method, path, response.getStatus(), elapsedMillis(request));
            }
        }
    }

    /**
     * Skips logging for excluded paths such as actuator health checks.
     *
     * @param request current request
     * @return {@code true} when the path is excluded
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return loggingProperties.excludedPaths().stream().anyMatch(path::startsWith);
    }

    /**
     * Ensures the END line is written when an asynchronous request completes.
     *
     * @return always {@code false}
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    /**
     * Computes the request duration from the start time stored as request attribute.
     *
     * @param request current request
     * @return elapsed milliseconds, or {@code -1} when the start time is unknown
     */
    private static long elapsedMillis(HttpServletRequest request) {
        Object start = request.getAttribute(AppConstants.REQUEST_START_TIME_ATTRIBUTE);
        return start instanceof Long startNanos ? TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos) : -1;
    }
}
