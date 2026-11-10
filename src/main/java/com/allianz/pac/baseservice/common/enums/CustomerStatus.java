package com.allianz.pac.baseservice.common.enums;

/**
 * Lifecycle status of a customer.
 *
 * <p>Shared by the API (DTOs) and the persistence layer (entity) so that DTOs never depend on the
 * {@code model} package.
 */
public enum CustomerStatus {
    /** Customer can use all services. */
    ACTIVE,
    /** Customer is deactivated. */
    INACTIVE
}
