package com.allianz.pac.baseservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.allianz.pac.baseservice.common.response.ApiResponse;
import com.allianz.pac.baseservice.common.response.ResponseStatus;
import com.allianz.pac.baseservice.support.ResponseFactorySupport;
import jakarta.servlet.RequestDispatcher;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link CustomErrorController}.
 */
class CustomErrorControllerTest {

    private final CustomErrorController controller = new CustomErrorController(ResponseFactorySupport.responseFactory());

    /**
     * Verifies that a container 404 is rendered as the common envelope.
     */
    @Test
    @DisplayName("renders container 404 as failure envelope")
    void rendersNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/unknown");

        ResponseEntity<ApiResponse<Map<String, String>>> response = controller.handleError(request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(ResponseStatus.FAILURE);
        assertThat(response.getBody().message()).isEqualTo("Requested endpoint does not exist");
    }

    /**
     * Verifies that an application exception thrown in a filter keeps its error code.
     */
    @Test
    @DisplayName("renders application exceptions with their error code")
    void rendersApplicationException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new ResourceNotFoundException("Customer", "id", 7));

        ResponseEntity<ApiResponse<Map<String, String>>> response = controller.handleError(request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Customer not found with id: 7");
    }

    /**
     * Verifies that unexpected exceptions without status become a 500.
     */
    @Test
    @DisplayName("renders unexpected exceptions as 500")
    void rendersUnexpectedException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("boom"));

        ResponseEntity<ApiResponse<Map<String, String>>> response = controller.handleError(request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }
}
