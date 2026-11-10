package com.allianz.pac.baseservice.common.constants;

/**
 * Keys of user-facing messages defined in {@code messages.properties}.
 *
 * <p>Message texts are never hard-coded in Java code; always reference a key from this class.
 */
public final class MessageKeys {

    public static final String CUSTOMER_CREATED = "customer.created";
    public static final String CUSTOMERS_CREATED = "customers.created";
    public static final String CUSTOMER_FETCHED = "customer.fetched";
    public static final String CUSTOMERS_FETCHED = "customers.fetched";
    public static final String CUSTOMER_UPDATED = "customer.updated";
    public static final String CUSTOMER_DELETED = "customer.deleted";
    public static final String CUSTOMER_CREDIT_SCORE_FETCHED = "customer.credit-score.fetched";

    /**
     * Prevents instantiation of this constants holder.
     */
    private MessageKeys() {
        throw new UnsupportedOperationException("Constants class must not be instantiated");
    }
}
