package com.allianz.pac.baseservice.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allianz.pac.baseservice.common.response.ApiResponseFactory;
import com.allianz.pac.baseservice.config.properties.LoggingProperties;
import com.allianz.pac.baseservice.dto.request.CustomerRequest;
import com.allianz.pac.baseservice.dto.response.CreditScoreResponse;
import com.allianz.pac.baseservice.exception.ResourceNotFoundException;
import com.allianz.pac.baseservice.service.CustomerService;
import com.allianz.pac.baseservice.support.TestFixtures;
import com.allianz.pac.baseservice.util.MessageResolver;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Web-layer slice tests for {@link CustomerController}: request mapping, validation, response
 * envelope and exception translation. The service is mocked.
 */
@WebMvcTest(CustomerController.class)
@ActiveProfiles("test")
@Import({ApiResponseFactory.class, MessageResolver.class, CustomerControllerTest.PropertiesConfig.class})
class CustomerControllerTest {

    private static final String BASE_URL = "/api/v1/customers";

    /**
     * Registers the configuration properties needed by the servlet filters in this slice.
     */
    @TestConfiguration
    @EnableConfigurationProperties(LoggingProperties.class)
    static class PropertiesConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    /**
     * Builds a JSON customer request body.
     *
     * @param email e-mail value
     * @return JSON text
     */
    private static String customerJson(String email) {
        return """
                {"firstName":"John","lastName":"Doe","email":"%s","phoneNumber":"+49 123456789","status":"ACTIVE"}
                """.formatted(email);
    }

    /**
     * Verifies creation returns 201 with the success envelope and a correlation id header.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("POST creates a customer -> 201 SUCCESS envelope")
    void createsCustomer() throws Exception {
        when(customerService.createCustomer(any(CustomerRequest.class)))
                .thenReturn(TestFixtures.customerResponse(1L, "john@test.com"));

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(customerJson("john@test.com")))
                .andExpect(status().isCreated())
                .andExpect(header().exists(TestFixtures.CORRELATION_HEADER))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.message").value("Customer created successfully"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.correlationId", notNullValue()));
    }

    /**
     * Verifies Bean Validation errors return 400 with field errors in {@code data}.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("POST with invalid body -> 400 FAILURE with field errors")
    void rejectsInvalidBody() throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(customerJson("not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.email", notNullValue()));
    }

    /**
     * Verifies malformed JSON returns 400.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("POST with malformed JSON -> 400")
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    /**
     * Verifies unsupported content types return 415.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("POST with text/plain -> 415")
    void rejectsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value("FAILURE"));
    }

    /**
     * Verifies bulk creation.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("POST /bulk creates customers -> 201")
    void createsBulk() throws Exception {
        when(customerService.createCustomers(anyList())).thenReturn(List.of(
                TestFixtures.customerResponse(1L, "a@test.com"), TestFixtures.customerResponse(2L, "b@test.com")));

        mockMvc.perform(post(BASE_URL + "/bulk").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customers\":[" + customerJson("a@test.com") + "," + customerJson("b@test.com") + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("2 customers created successfully"))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    /**
     * Verifies lookup by id.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("GET /{id} returns the customer")
    void getsCustomer() throws Exception {
        when(customerService.getCustomerById(1L)).thenReturn(TestFixtures.customerResponse(1L, "a@test.com"));

        mockMvc.perform(get(BASE_URL + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("a@test.com"));
    }

    /**
     * Verifies not-found errors from the service.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("GET /{id} unknown -> 404 FAILURE")
    void getsUnknownCustomer() throws Exception {
        when(customerService.getCustomerById(9L)).thenThrow(new ResourceNotFoundException("Customer", "id", 9L));

        mockMvc.perform(get(BASE_URL + "/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.statusCode").value(404))
                .andExpect(jsonPath("$.message").value("Customer not found with id: 9"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    /**
     * Verifies path variable constraint validation.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("GET /{id} with id 0 -> 400 with parameter error")
    void rejectsInvalidId() throws Exception {
        mockMvc.perform(get(BASE_URL + "/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.id", notNullValue()));
    }

    /**
     * Verifies type conversion errors of path variables.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("GET /{id} with non-numeric id -> 400")
    void rejectsNonNumericId() throws Exception {
        mockMvc.perform(get(BASE_URL + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'id' has an invalid value"));
    }

    /**
     * Verifies update.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("PUT /{id} updates the customer")
    void updatesCustomer() throws Exception {
        when(customerService.updateCustomer(eq(1L), any(CustomerRequest.class)))
                .thenReturn(TestFixtures.customerResponse(1L, "new@test.com"));

        mockMvc.perform(put(BASE_URL + "/1").contentType(MediaType.APPLICATION_JSON).content(customerJson("new@test.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Customer updated successfully"));
    }

    /**
     * Verifies delete returns an empty data object.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("DELETE /{id} -> 200 with empty data")
    void deletesCustomer() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Customer deleted successfully"))
                .andExpect(jsonPath("$.data").isEmpty());
        verify(customerService).deleteCustomer(1L);
    }

    /**
     * Verifies unsupported HTTP methods return 405 with the envelope.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("PATCH -> 405 FAILURE")
    void rejectsUnsupportedMethod() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(BASE_URL + "/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value("FAILURE"));
    }

    /**
     * Verifies the asynchronous credit-score endpoint.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("GET /{id}/credit-score is processed asynchronously")
    void getsCreditScoreAsync() throws Exception {
        when(customerService.getCreditScoreAsync(1L)).thenReturn(
                CompletableFuture.completedFuture(new CreditScoreResponse(1L, 710, "A", Instant.now())));

        MvcResult asyncResult = mockMvc.perform(get(BASE_URL + "/1/credit-score"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(710));
    }

    /**
     * Verifies asynchronous failures are rendered with the envelope.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("GET /{id}/credit-score async failure -> 404 FAILURE")
    void creditScoreAsyncFailure() throws Exception {
        when(customerService.getCreditScoreAsync(9L)).thenReturn(
                CompletableFuture.failedFuture(new ResourceNotFoundException("Customer", "id", 9L)));

        MvcResult asyncResult = mockMvc.perform(get(BASE_URL + "/9/credit-score"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("FAILURE"));
    }
}
