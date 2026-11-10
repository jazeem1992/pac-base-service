package com.allianz.pac.baseservice.dto.response;

import com.allianz.pac.baseservice.model.CustomerStatus;
import java.time.Instant;

/**
 * Customer representation returned by the API (also the value stored in the Redis cache).
 *
 * @param id          customer id
 * @param firstName   first name
 * @param lastName    last name
 * @param email       e-mail address
 * @param phoneNumber phone number
 * @param status      customer status
 * @param createdAt   creation timestamp (UTC)
 * @param updatedAt   last update timestamp (UTC)
 */
public record CustomerResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        CustomerStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
