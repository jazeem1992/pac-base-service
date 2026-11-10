package com.allianz.pac.baseservice.repository;

import com.allianz.pac.baseservice.model.Customer;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Data access for {@link Customer} entities.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Checks whether a customer with the given e-mail exists.
     *
     * @param email e-mail address (lower case)
     * @return {@code true} if a customer uses this e-mail
     */
    boolean existsByEmail(String email);

    /**
     * Checks whether ANOTHER customer (different id) uses the given e-mail.
     *
     * @param email e-mail address (lower case)
     * @param id    id of the customer to exclude
     * @return {@code true} if another customer uses this e-mail
     */
    boolean existsByEmailAndIdNot(String email, Long id);

    /**
     * Returns those e-mails of the given collection that are already used - one query for a whole
     * bulk request instead of one query per item.
     *
     * @param emails e-mail addresses to check (lower case)
     * @return e-mails already present in the database
     */
    @Query("select c.email from Customer c where c.email in :emails")
    List<String> findExistingEmails(@Param("emails") Collection<String> emails);
}
