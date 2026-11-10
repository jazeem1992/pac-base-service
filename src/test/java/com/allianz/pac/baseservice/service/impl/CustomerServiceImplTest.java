package com.allianz.pac.baseservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.allianz.pac.baseservice.client.CreditScoreClient;
import com.allianz.pac.baseservice.dto.request.CustomerRequest;
import com.allianz.pac.baseservice.dto.response.CreditScoreResponse;
import com.allianz.pac.baseservice.dto.response.CustomerResponse;
import com.allianz.pac.baseservice.dto.response.PageResponse;
import com.allianz.pac.baseservice.exception.DuplicateResourceException;
import com.allianz.pac.baseservice.exception.ResourceNotFoundException;
import com.allianz.pac.baseservice.mapper.CustomerMapper;
import com.allianz.pac.baseservice.mapper.CustomerMapperImpl;
import com.allianz.pac.baseservice.model.Customer;
import com.allianz.pac.baseservice.common.enums.CustomerStatus;
import com.allianz.pac.baseservice.repository.CustomerRepository;
import com.allianz.pac.baseservice.support.TestFixtures;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Unit tests for {@link CustomerServiceImpl} with mocked repository and client and the real
 * MapStruct mapper.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CreditScoreClient creditScoreClient;

    private final CustomerMapper customerMapper = new CustomerMapperImpl();

    private CustomerServiceImpl service;

    /**
     * Creates the service under test.
     */
    @BeforeEach
    void setUp() {
        service = new CustomerServiceImpl(customerRepository, customerMapper, creditScoreClient);
    }

    /**
     * Creates a persisted-looking customer entity.
     *
     * @param id    id
     * @param email e-mail
     * @return the entity
     */
    private static Customer customer(Long id, String email) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setFirstName("John");
        customer.setLastName("Doe");
        customer.setEmail(email);
        customer.setStatus(CustomerStatus.ACTIVE);
        customer.setCreatedAt(Instant.now());
        customer.setUpdatedAt(Instant.now());
        return customer;
    }

    /**
     * Tests of {@link CustomerServiceImpl#createCustomer(CustomerRequest)}.
     */
    @Nested
    @DisplayName("createCustomer")
    class CreateCustomer {

        /**
         * Verifies the e-mail is normalized and the customer saved.
         */
        @Test
        @DisplayName("normalizes the e-mail and saves the customer")
        void createsCustomer() {
            when(customerRepository.existsByEmail("john@test.com")).thenReturn(false);
            when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
                Customer saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            CustomerResponse response = service.createCustomer(TestFixtures.customerRequest("  John@Test.com "));

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.email()).isEqualTo("john@test.com");
        }

        /**
         * Verifies duplicates are rejected without saving.
         */
        @Test
        @DisplayName("rejects a duplicate e-mail")
        void rejectsDuplicate() {
            when(customerRepository.existsByEmail("john@test.com")).thenReturn(true);
            CustomerRequest request = TestFixtures.customerRequest("john@test.com");

            assertThatThrownBy(() -> service.createCustomer(request)).isInstanceOf(DuplicateResourceException.class);
            verify(customerRepository, never()).save(any());
        }
    }

    /**
     * Tests of {@link CustomerServiceImpl#createCustomers(List)}.
     */
    @Nested
    @DisplayName("createCustomers")
    class CreateCustomers {

        /**
         * Verifies that all customers are saved in one batch.
         */
        @Test
        @DisplayName("saves all customers in one batch")
        @SuppressWarnings("unchecked")
        void createsAll() {
            when(customerRepository.findExistingEmails(anyCollection())).thenReturn(List.of());
            when(customerRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

            List<CustomerResponse> responses = service.createCustomers(List.of(
                    TestFixtures.customerRequest("a@test.com"), TestFixtures.customerRequest("b@test.com")));

            ArgumentCaptor<List<Customer>> captor = ArgumentCaptor.forClass(List.class);
            verify(customerRepository).saveAllAndFlush(captor.capture());
            assertThat(captor.getValue()).hasSize(2);
            assertThat(responses).extracting(CustomerResponse::email).containsExactly("a@test.com", "b@test.com");
        }

        /**
         * Verifies duplicates inside the request abort the whole batch.
         */
        @Test
        @DisplayName("rejects duplicates inside the request")
        void rejectsDuplicatesInRequest() {
            List<CustomerRequest> requests = List.of(
                    TestFixtures.customerRequest("a@test.com"), TestFixtures.customerRequest("A@test.com"));

            assertThatThrownBy(() -> service.createCustomers(requests)).isInstanceOf(DuplicateResourceException.class);
            verify(customerRepository, never()).saveAllAndFlush(anyList());
        }

        /**
         * Verifies e-mails already present in the database abort the whole batch.
         */
        @Test
        @DisplayName("rejects e-mails already stored")
        void rejectsExistingEmails() {
            when(customerRepository.findExistingEmails(anyCollection())).thenReturn(List.of("a@test.com"));
            List<CustomerRequest> requests = List.of(TestFixtures.customerRequest("a@test.com"));

            assertThatThrownBy(() -> service.createCustomers(requests)).isInstanceOf(DuplicateResourceException.class);
            verify(customerRepository, never()).saveAllAndFlush(anyList());
        }
    }

    /**
     * Tests of the read operations.
     */
    @Nested
    @DisplayName("read operations")
    class ReadOperations {

        /**
         * Verifies lookup by id.
         */
        @Test
        @DisplayName("getCustomerById returns the customer")
        void getById() {
            when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "a@test.com")));

            assertThat(service.getCustomerById(1L).email()).isEqualTo("a@test.com");
        }

        /**
         * Verifies not-found handling.
         */
        @Test
        @DisplayName("getCustomerById fails for unknown id")
        void getByIdNotFound() {
            when(customerRepository.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCustomerById(9L)).isInstanceOf(ResourceNotFoundException.class);
        }

        /**
         * Verifies paging.
         */
        @Test
        @DisplayName("getCustomers maps the page")
        void getPage() {
            PageRequest pageable = PageRequest.of(0, 10);
            when(customerRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(customer(1L, "a@test.com")), pageable, 1));

            PageResponse<CustomerResponse> page = service.getCustomers(pageable);

            assertThat(page.content()).hasSize(1);
            assertThat(page.totalElements()).isEqualTo(1);
            assertThat(page.last()).isTrue();
        }
    }

    /**
     * Tests of update and delete.
     */
    @Nested
    @DisplayName("update and delete")
    class Modifications {

        /**
         * Verifies the update copies the new values.
         */
        @Test
        @DisplayName("updateCustomer applies the new values")
        void updates() {
            Customer existing = customer(1L, "old@test.com");
            when(customerRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(customerRepository.existsByEmailAndIdNot("new@test.com", 1L)).thenReturn(false);
            when(customerRepository.saveAndFlush(existing)).thenReturn(existing);

            CustomerResponse response = service.updateCustomer(1L, TestFixtures.customerRequest("New@test.com"));

            assertThat(response.email()).isEqualTo("new@test.com");
        }

        /**
         * Verifies the update rejects an e-mail used by another customer.
         */
        @Test
        @DisplayName("updateCustomer rejects an e-mail used by another customer")
        void updateRejectsDuplicate() {
            when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "old@test.com")));
            when(customerRepository.existsByEmailAndIdNot("b@test.com", 1L)).thenReturn(true);
            CustomerRequest request = TestFixtures.customerRequest("b@test.com");

            assertThatThrownBy(() -> service.updateCustomer(1L, request)).isInstanceOf(DuplicateResourceException.class);
        }

        /**
         * Verifies deletion of an existing customer.
         */
        @Test
        @DisplayName("deleteCustomer deletes the entity")
        void deletes() {
            Customer existing = customer(1L, "a@test.com");
            when(customerRepository.findById(1L)).thenReturn(Optional.of(existing));

            service.deleteCustomer(1L);

            verify(customerRepository).delete(existing);
        }
    }

    /**
     * Tests of the asynchronous credit score lookup.
     */
    @Nested
    @DisplayName("getCreditScoreAsync")
    class CreditScore {

        /**
         * Verifies the downstream result is returned.
         */
        @Test
        @DisplayName("returns the downstream credit score")
        void returnsScore() {
            CreditScoreResponse score = new CreditScoreResponse(1L, 700, "A", Instant.now());
            when(customerRepository.existsById(1L)).thenReturn(true);
            when(creditScoreClient.fetchCreditScore(1L)).thenReturn(score);

            assertThat(service.getCreditScoreAsync(1L)).isCompletedWithValue(score);
        }

        /**
         * Verifies unknown customers are rejected before calling the downstream service.
         */
        @Test
        @DisplayName("fails for unknown customers without remote call")
        void unknownCustomer() {
            when(customerRepository.existsById(9L)).thenReturn(false);

            assertThatThrownBy(() -> service.getCreditScoreAsync(9L)).isInstanceOf(ResourceNotFoundException.class);
            verify(creditScoreClient, never()).fetchCreditScore(any());
        }
    }
}
