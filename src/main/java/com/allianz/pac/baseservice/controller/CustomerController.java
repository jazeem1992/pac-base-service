package com.allianz.pac.baseservice.controller;

import com.allianz.pac.baseservice.common.constants.MessageKeys;
import com.allianz.pac.baseservice.common.constants.ResilienceInstances;
import com.allianz.pac.baseservice.common.response.ApiResponse;
import com.allianz.pac.baseservice.common.response.ApiResponseFactory;
import com.allianz.pac.baseservice.dto.request.BulkCustomerRequest;
import com.allianz.pac.baseservice.dto.request.CustomerRequest;
import com.allianz.pac.baseservice.dto.response.CreditScoreResponse;
import com.allianz.pac.baseservice.dto.response.CustomerResponse;
import com.allianz.pac.baseservice.dto.response.PageResponse;
import com.allianz.pac.baseservice.service.CustomerService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sample REST API for customers. Copy this as a template for new controllers.
 *
 * <p>Conventions: thin controller (no business logic), validation via annotations, every response
 * wrapped in {@link ApiResponse} through {@link ApiResponseFactory}, base path from
 * {@code app.api.base-path}, rate limited by the {@code customerApi} Resilience4j instance.
 */
@RestController
@RequestMapping("${app.api.base-path}/customers")
@RateLimiter(name = ResilienceInstances.CUSTOMER_API)
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Sample customer management API")
public class CustomerController {

    private final CustomerService customerService;
    private final ApiResponseFactory responseFactory;

    /**
     * Creates a customer.
     *
     * @param request customer data
     * @return 201 with the created customer
     */
    @PostMapping
    @Operation(summary = "Create a customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(@Valid @RequestBody CustomerRequest request) {
        return responseFactory.created(MessageKeys.CUSTOMER_CREATED, customerService.createCustomer(request));
    }

    /**
     * Creates many customers in one all-or-nothing transaction.
     *
     * @param request customers to create
     * @return 201 with the created customers
     */
    @PostMapping("/bulk")
    @Operation(summary = "Create customers in bulk (all or nothing)")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> createCustomers(@Valid @RequestBody BulkCustomerRequest request) {
        List<CustomerResponse> created = customerService.createCustomers(request.customers());
        return responseFactory.created(MessageKeys.CUSTOMERS_CREATED, created, created.size());
    }

    /**
     * Returns a customer by id.
     *
     * @param id customer id
     * @return 200 with the customer
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a customer by id")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomer(@PathVariable @Positive Long id) {
        return responseFactory.ok(MessageKeys.CUSTOMER_FETCHED, customerService.getCustomerById(id));
    }

    /**
     * Returns a page of customers. Page size defaults/limits come from
     * {@code spring.data.web.pageable.*}.
     *
     * @param pageable page, size and sort parameters
     * @return 200 with the page
     */
    @GetMapping
    @Operation(summary = "List customers (paginated)")
    public ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> getCustomers(Pageable pageable) {
        return responseFactory.ok(MessageKeys.CUSTOMERS_FETCHED, customerService.getCustomers(pageable));
    }

    /**
     * Replaces the data of a customer.
     *
     * @param id      customer id
     * @param request new customer data
     * @return 200 with the updated customer
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update a customer")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(@PathVariable @Positive Long id,
                                                                        @Valid @RequestBody CustomerRequest request) {
        return responseFactory.ok(MessageKeys.CUSTOMER_UPDATED, customerService.updateCustomer(id, request));
    }

    /**
     * Deletes a customer.
     *
     * @param id customer id
     * @return 200 with an empty {@code data} object
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a customer")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteCustomer(@PathVariable @Positive Long id) {
        customerService.deleteCustomer(id);
        return responseFactory.ok(MessageKeys.CUSTOMER_DELETED, Map.of());
    }

    /**
     * Returns the credit score of a customer from the downstream credit-score service. Processed
     * asynchronously: the servlet thread is released while the remote call runs
     * (timeout: {@code spring.mvc.async.request-timeout}).
     *
     * @param id customer id
     * @return a future completing with 200 and the credit score
     */
    @GetMapping("/{id}/credit-score")
    @Operation(summary = "Get the credit score of a customer (async, resilient downstream call)")
    public CompletableFuture<ResponseEntity<ApiResponse<CreditScoreResponse>>> getCreditScore(@PathVariable @Positive Long id) {
        return customerService.getCreditScoreAsync(id)
                .thenApply(score -> responseFactory.ok(MessageKeys.CUSTOMER_CREDIT_SCORE_FETCHED, score));
    }
}
