package com.allianz.pac.baseservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.allianz.pac.baseservice.config.JpaAuditingConfig;
import com.allianz.pac.baseservice.config.properties.AuditProperties;
import com.allianz.pac.baseservice.model.Customer;
import com.allianz.pac.baseservice.common.enums.CustomerStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * JPA slice tests for {@link CustomerRepository} against the Flyway schema (H2 in PostgreSQL mode).
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@EnableConfigurationProperties(AuditProperties.class)
class CustomerRepositoryTest {

    @Autowired
    private CustomerRepository customerRepository;

    /**
     * Creates an unsaved customer entity.
     *
     * @param email e-mail
     * @return the entity
     */
    private static Customer newCustomer(String email) {
        Customer customer = new Customer();
        customer.setFirstName("John");
        customer.setLastName("Doe");
        customer.setEmail(email);
        customer.setStatus(CustomerStatus.ACTIVE);
        return customer;
    }

    /**
     * Verifies id generation from the sequence and automatic auditing.
     */
    @Test
    @DisplayName("save assigns id, audit fields and version")
    void savesWithAuditing() {
        Customer saved = customerRepository.saveAndFlush(newCustomer("audit@test.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo("SYSTEM");
        assertThat(saved.getUpdatedBy()).isEqualTo("SYSTEM");
        assertThat(saved.getVersion()).isZero();
    }

    /**
     * Verifies the e-mail existence queries.
     */
    @Test
    @DisplayName("existsByEmail / existsByEmailAndIdNot")
    void emailExistenceQueries() {
        Customer saved = customerRepository.saveAndFlush(newCustomer("exists@test.com"));

        assertThat(customerRepository.existsByEmail("exists@test.com")).isTrue();
        assertThat(customerRepository.existsByEmail("missing@test.com")).isFalse();
        assertThat(customerRepository.existsByEmailAndIdNot("exists@test.com", saved.getId())).isFalse();
        assertThat(customerRepository.existsByEmailAndIdNot("exists@test.com", saved.getId() + 1)).isTrue();
    }

    /**
     * Verifies the bulk duplicate check query.
     */
    @Test
    @DisplayName("findExistingEmails returns only stored e-mails")
    void findExistingEmails() {
        customerRepository.saveAllAndFlush(List.of(newCustomer("one@test.com"), newCustomer("two@test.com")));

        assertThat(customerRepository.findExistingEmails(List.of("one@test.com", "three@test.com")))
                .containsExactly("one@test.com");
    }

    /**
     * Verifies the unique constraint of the schema.
     */
    @Test
    @DisplayName("unique e-mail constraint is enforced by the database")
    void uniqueEmailConstraint() {
        customerRepository.saveAndFlush(newCustomer("dup@test.com"));
        Customer duplicate = newCustomer("dup@test.com");

        assertThatThrownBy(() -> customerRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
