package com.allianz.pac.baseservice.exception;

import com.allianz.pac.baseservice.common.response.ApiResponse;
import com.allianz.pac.baseservice.common.response.ApiResponseFactory;
import com.allianz.pac.baseservice.util.LogSanitizer;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's default {@code /error} page so that errors raised OUTSIDE the MVC layer
 * (servlet filters, container errors) also return the common {@link ApiResponse} envelope.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CustomErrorController implements ErrorController {

    private final ApiResponseFactory responseFactory;

    /**
     * Renders any servlet container error as a failure envelope.
     *
     * @param request the error dispatch request carrying the {@code jakarta.servlet.error.*} attributes
     * @return failure envelope matching the original HTTP status
     */
    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<ApiResponse<Map<String, String>>> handleError(HttpServletRequest request) {
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (exception instanceof BaseException baseException) {
            log.warn("Request failed outside MVC [{}]: {}", baseException.getErrorCode(), LogSanitizer.clean(baseException.getMessage()));
            return responseFactory.failure(baseException.getErrorCode(), baseException.getDetails(), baseException.getMessageArgs());
        }
        ErrorCode errorCode = ErrorCode.fromHttpStatus(resolveStatus(request));
        if (exception instanceof Throwable throwable) {
            log.error("Request failed outside MVC [{}] for uri {}", errorCode,
                    LogSanitizer.clean(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)), throwable);
        } else {
            log.warn("Request failed outside MVC [{}] for uri {}", errorCode,
                    LogSanitizer.clean(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)));
        }
        return responseFactory.failure(errorCode, null);
    }

    /**
     * Reads the original HTTP status from the error dispatch attributes.
     *
     * @param request error dispatch request
     * @return the original status code, 500 if unknown
     */
    private int resolveStatus(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        return status instanceof Integer code ? code : 500;
    }
}
