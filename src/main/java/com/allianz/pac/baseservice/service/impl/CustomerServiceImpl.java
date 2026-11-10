package com.allianz.pac.baseservice.service.impl;

import com.allianz.pac.baseservice.client.CreditScoreClient;
import com.allianz.pac.baseservice.common.annotation.TransactionalRead;
import com.allianz.pac.baseservice.common.annotation.TransactionalWrite;
import com.allianz.pac.baseservice.common.constants.CacheNames;
import com.allianz.pac.baseservice.dto.request.CustomerRequest;
import com.allianz.pac.baseservice.dto.response.CreditScoreResponse;
import com.allianz.pac.baseservice.dto.response.CustomerResponse;
import com.allianz.pac.baseservice.dto.response.PageResponse;
import com.allianz.pac.baseservice.exception.DuplicateResourceException;
import com.allianz.pac.baseservice.exception.ResourceNotFoundException;
import com.allianz.pac.baseservice.mapper.CustomerMapper;
import com.allianz.pac.baseservice.model.Customer;
import com.allianz.pac.baseservice.repository.CustomerRepository;
import com.allianz.pac.baseservice.service.CustomerService;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link CustomerService}.
 *
 * <p>Demonstrates the project conventions: read-only transactions by default,
 * {@link TransactionalWrite} (rollback on any exception) for modifications, Redis caching, and
 * {@code @Async} for slow remote calls. Method START/END logs are written by the logging aspect.
 */
@Service
@TransactionalRead
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private static final String RESOURCE_NAME = "Customer";
    private static final String FIELD_ID = "id";
    private static final String FIELD_EMAIL = "email";

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;
    private final CreditScoreClient creditScoreClient;

    /**
     * {@inheritDoc}
     *
     * @throws DuplicateResourceException when the e-mail is already used
     */
    @Override
    @TransactionalWrite
    @CachePut(cacheNames = CacheNames.CUSTOMERS, key = "#result.id()")
    public CustomerResponse createCustomer(CustomerRequest request) {
        String email = normalizeEmail(request.email());
        if (customerRepository.existsByEmail(email)) {
            throw new DuplicateResourceException(RESOURCE_NAME, FIELD_EMAIL, email);
        }
        Customer customer = customerMapper.toEntity(request);
        customer.setEmail(email);
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Duplicates are checked with one query for the whole batch; inserts are JDBC-batched. Any
     * exception rolls the complete transaction back.
     *
     * @throws DuplicateResourceException when an e-mail is repeated in the request or already used
     */
    @Override
    @TransactionalWrite
    public List<CustomerResponse> createCustomers(List<CustomerRequest> requests) {
        Set<String> emails = new HashSet<>();
        List<Customer> customers = requests.stream().map(request -> {
            String email = normalizeEmail(request.email());
            if (!emails.add(email)) {
                throw new DuplicateResourceException(RESOURCE_NAME, FIELD_EMAIL, email);
            }
            Customer customer = customerMapper.toEntity(request);
            customer.setEmail(email);
            return customer;
        }).toList();

        List<String> existing = customerRepository.findExistingEmails(emails);
        if (!existing.isEmpty()) {
            throw new DuplicateResourceException(RESOURCE_NAME, FIELD_EMAIL, existing.getFirst());
        }
        return customerRepository.saveAllAndFlush(customers).stream().map(customerMapper::toResponse).toList();
    }

    /**
     * {@inheritDoc}
     *
     * @throws ResourceNotFoundException when no customer has this id
     */
    @Override
    @Cacheable(cacheNames = CacheNames.CUSTOMERS, key = "#id")
    public CustomerResponse getCustomerById(Long id) {
        return customerMapper.toResponse(findCustomer(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResponse<CustomerResponse> getCustomers(Pageable pageable) {
        return PageResponse.from(customerRepository.findAll(pageable).map(customerMapper::toResponse));
    }

    /**
     * {@inheritDoc}
     *
     * @throws ResourceNotFoundException  when no customer has this id
     * @throws DuplicateResourceException when another customer already uses the new e-mail
     */
    @Override
    @TransactionalWrite
    @CachePut(cacheNames = CacheNames.CUSTOMERS, key = "#id")
    public CustomerResponse updateCustomer(Long id, CustomerRequest request) {
        Customer customer = findCustomer(id);
        String email = normalizeEmail(request.email());
        if (customerRepository.existsByEmailAndIdNot(email, id)) {
            throw new DuplicateResourceException(RESOURCE_NAME, FIELD_EMAIL, email);
        }
        customerMapper.updateEntity(request, customer);
        customer.setEmail(email);
        return customerMapper.toResponse(customerRepository.saveAndFlush(customer));
    }

    /**
     * {@inheritDoc}
     *
     * @throws ResourceNotFoundException when no customer has this id
     */
    @Override
    @TransactionalWrite
    @CacheEvict(cacheNames = CacheNames.CUSTOMERS, key = "#id")
    public void deleteCustomer(Long id) {
        customerRepository.delete(findCustomer(id));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Runs on the application task executor (virtual threads); exceptions complete the future
     * exceptionally and are rendered by the global exception handler. No transaction is held during
     * the remote call, so no database connection is blocked while waiting for the downstream service.
     *
     * @throws ResourceNotFoundException when no customer has this id
     */
    @Override
    @Async
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CompletableFuture<CreditScoreResponse> getCreditScoreAsync(Long id) {
        if (!customerRepository.existsById(id)) {
            throw new ResourceNotFoundException(RESOURCE_NAME, FIELD_ID, id);
        }
        return CompletableFuture.completedFuture(creditScoreClient.fetchCreditScore(id));
    }

    /**
     * Loads a customer or fails with a not-found error.
     *
     * @param id customer id
     * @return the customer entity
     * @throws ResourceNotFoundException when no customer has this id
     */
    private Customer findCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, FIELD_ID, id));
    }

    /**
     * Normalizes an e-mail address for storage and uniqueness checks.
     *
     * @param email raw e-mail
     * @return trimmed, lower-case e-mail
     */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
