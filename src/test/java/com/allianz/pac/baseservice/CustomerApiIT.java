package com.allianz.pac.baseservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
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

import com.allianz.pac.baseservice.client.CreditScoreClient;
import com.allianz.pac.baseservice.dto.response.CreditScoreResponse;
import com.allianz.pac.baseservice.exception.ErrorCode;
import com.allianz.pac.baseservice.exception.ExternalServiceException;
import com.allianz.pac.baseservice.repository.CustomerRepository;
import com.allianz.pac.baseservice.support.TestFixtures;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end integration tests of the customer API with the full application context
 * (filters, aspects, transactions, cache, Flyway schema on H2). Only the downstream credit-score
 * client is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerApiIT {

    private static final String BASE_URL = "/api/v1/customers";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private CreditScoreClient creditScoreClient;

    /**
     * Cleans database and caches before each test.
     */
    @BeforeEach
    void cleanUp() {
        customerRepository.deleteAll();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    /**
     * Builds a JSON customer request body.
     *
     * @param email e-mail value
     * @return JSON text
     */
    private static String customerJson(String email) {
        return """
                {"firstName":"Jane","lastName":"Roe","email":"%s","phoneNumber":"+49 555123456","status":"ACTIVE"}
                """.formatted(email);
    }

    /**
     * Creates a customer through the API and returns its id.
     *
     * @param email e-mail value
     * @return the new customer id
     * @throws Exception when the request fails
     */
    private long createCustomer(String email) throws Exception {
        MvcResult result = mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(customerJson(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    /**
     * Verifies the full CRUD lifecycle and the response envelope.
     *
     * @throws Exception when a request fails
     */
    @Test
    @DisplayName("create -> get -> list -> update -> delete")
    void crudLifecycle() throws Exception {
        long id = createCustomer("Jane@Test.com");

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.message").value("Customer fetched successfully"))
                .andExpect(jsonPath("$.data.email").value("jane@test.com"))
                .andExpect(jsonPath("$.correlationId", notNullValue()));

        mockMvc.perform(get(BASE_URL).param("page", "0").param("size", "10").param("sort", "lastName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(id));

        mockMvc.perform(put(BASE_URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(customerJson("new@test.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("new@test.com"));

        mockMvc.perform(delete(BASE_URL + "/" + id)).andExpect(status().isOk());
        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("FAILURE"));
    }

    /**
     * Verifies that unknown JSON properties are ignored instead of failing the request.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("unknown JSON properties are ignored")
    void ignoresUnknownProperties() throws Exception {
        String body = """
                {"firstName":"Jane","lastName":"Roe","email":"extra@test.com","status":"ACTIVE",
                 "unknownField":"x","nested":{"a":1}}
                """;

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("extra@test.com"));
    }

    /**
     * Verifies duplicate e-mails are rejected with 409.
     *
     * @throws Exception when a request fails
     */
    @Test
    @DisplayName("duplicate e-mail -> 409 FAILURE")
    void rejectsDuplicates() throws Exception {
        createCustomer("dup@test.com");

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(customerJson("DUP@test.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.statusCode").value(409))
                .andExpect(jsonPath("$.message").value("Customer already exists with email: dup@test.com"));
    }

    /**
     * Verifies the bulk endpoint is all-or-nothing: a failing item rolls back the whole batch.
     *
     * @throws Exception when a request fails
     */
    @Test
    @DisplayName("bulk create rolls back completely when one item fails")
    void bulkRollback() throws Exception {
        createCustomer("existing@test.com");
        String body = "{\"customers\":[" + customerJson("first@test.com") + "," + customerJson("existing@test.com") + "]}";

        mockMvc.perform(post(BASE_URL + "/bulk").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        assertThat(customerRepository.existsByEmail("first@test.com")).isFalse();
        assertThat(customerRepository.count()).isEqualTo(1);
    }

    /**
     * Verifies successful bulk creation.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("bulk create stores all customers")
    void bulkCreate() throws Exception {
        String body = "{\"customers\":[" + customerJson("b1@test.com") + "," + customerJson("b2@test.com") + "]}";

        mockMvc.perform(post(BASE_URL + "/bulk").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.length()").value(2));

        assertThat(customerRepository.count()).isEqualTo(2);
    }

    /**
     * Verifies that an incoming correlation id is echoed in header and body.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("correlation id is propagated to response header and body")
    void propagatesCorrelationId() throws Exception {
        mockMvc.perform(get(BASE_URL + "/12345").header(TestFixtures.CORRELATION_HEADER, "it-corr-1"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(TestFixtures.CORRELATION_HEADER, "it-corr-1"))
                .andExpect(jsonPath("$.correlationId").value("it-corr-1"));
    }

    /**
     * Verifies unknown endpoints and invalid sort properties use the envelope.
     *
     * @throws Exception when a request fails
     */
    @Test
    @DisplayName("unknown endpoint -> 404, invalid sort -> 400")
    void requestErrors() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.message").value("Requested endpoint does not exist"));

        mockMvc.perform(get(BASE_URL).param("sort", "unknownProperty"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sorting by 'unknownProperty' is not supported"));
    }

    /**
     * Verifies the asynchronous credit-score endpoint end to end (async executor + MDC).
     *
     * @throws Exception when a request fails
     */
    @Test
    @DisplayName("credit score is fetched asynchronously")
    void creditScoreAsync() throws Exception {
        long id = createCustomer("score@test.com");
        when(creditScoreClient.fetchCreditScore(id)).thenReturn(new CreditScoreResponse(id, 680, "B", Instant.now()));

        MvcResult asyncResult = mockMvc.perform(get(BASE_URL + "/" + id + "/credit-score")
                        .header(TestFixtures.CORRELATION_HEADER, "async-corr"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(680))
                .andExpect(jsonPath("$.correlationId").value("async-corr"));
    }

    /**
     * Verifies downstream failures surface as 502 through the async path.
     *
     * @throws Exception when a request fails
     */
    @Test
    @DisplayName("downstream failure -> 502 FAILURE")
    void creditScoreDownstreamFailure() throws Exception {
        long id = createCustomer("fail@test.com");
        when(creditScoreClient.fetchCreditScore(id))
                .thenThrow(new ExternalServiceException(ErrorCode.EXTERNAL_SERVICE_ERROR, "credit-score-service", null));

        MvcResult asyncResult = mockMvc.perform(get(BASE_URL + "/" + id + "/credit-score"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.statusCode").value(502));
    }

    /**
     * Verifies the actuator health endpoint is available for Consul health checks.
     *
     * @throws Exception when the request fails
     */
    @Test
    @DisplayName("actuator health is UP")
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
