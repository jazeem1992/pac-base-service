package com.allianz.pac.baseservice.service;

import com.allianz.pac.baseservice.dto.request.CustomerRequest;
import com.allianz.pac.baseservice.dto.response.CreditScoreResponse;
import com.allianz.pac.baseservice.dto.response.CustomerResponse;
import com.allianz.pac.baseservice.dto.response.PageResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.data.domain.Pageable;

/**
 * Business operations on customers.
 */
public interface CustomerService {

    /**
     * Creates a customer.
     *
     * @param request customer data
     * @return the created customer
     */
    CustomerResponse createCustomer(CustomerRequest request);

    /**
     * Creates many customers in ONE transaction: if any customer fails, none is stored (rollback).
     *
     * @param requests customers to create
     * @return the created customers
     */
    List<CustomerResponse> createCustomers(List<CustomerRequest> requests);

    /**
     * Returns a customer by id (served from cache when available).
     *
     * @param id customer id
     * @return the customer
     */
    CustomerResponse getCustomerById(Long id);

    /**
     * Returns a page of customers.
     *
     * @param pageable page number, size and sort
     * @return the requested page
     */
    PageResponse<CustomerResponse> getCustomers(Pageable pageable);

    /**
     * Replaces the data of an existing customer.
     *
     * @param id      customer id
     * @param request new customer data
     * @return the updated customer
     */
    CustomerResponse updateCustomer(Long id, CustomerRequest request);

    /**
     * Deletes a customer.
     *
     * @param id customer id
     */
    void deleteCustomer(Long id);

    /**
     * Fetches the credit score of a customer from the downstream service asynchronously, so the
     * request thread is released while the (potentially slow) remote call runs.
     *
     * @param id customer id
     * @return a future completing with the credit score
     */
    CompletableFuture<CreditScoreResponse> getCreditScoreAsync(Long id);
}
