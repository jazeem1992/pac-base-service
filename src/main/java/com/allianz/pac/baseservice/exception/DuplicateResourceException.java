package com.allianz.pac.baseservice.exception;

import java.io.Serial;

/**
 * Thrown when a resource with the same unique key already exists (HTTP 409).
 */
public class DuplicateResourceException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param resourceName name of the resource type, e.g. {@code "Customer"}
     * @param fieldName    unique field that clashes, e.g. {@code "email"}
     * @param fieldValue   clashing value
     */
    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(ErrorCode.DUPLICATE_RESOURCE, resourceName, fieldName, fieldValue);
    }
}
