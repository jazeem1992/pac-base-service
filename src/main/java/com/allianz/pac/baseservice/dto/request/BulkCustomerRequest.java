package com.allianz.pac.baseservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request payload to create many customers in one all-or-nothing transaction.
 *
 * @param customers customers to create (bounded to protect the service)
 */
@Schema(description = "Bulk customer creation request (all or nothing)")
public record BulkCustomerRequest(@NotEmpty @Size(max = 500) List<@Valid CustomerRequest> customers) {

    /**
     * Makes the list immutable and never {@code null}-unsafe for callers.
     */
    public BulkCustomerRequest {
        customers = customers == null ? null : List.copyOf(customers);
    }
}
