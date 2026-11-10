package com.allianz.pac.baseservice.exception;

import java.io.Serial;

/**
 * Thrown when a requested resource does not exist (HTTP 404).
 */
public class ResourceNotFoundException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param resourceName name of the resource type, e.g. {@code "Customer"}
     * @param fieldName    field used for the lookup, e.g. {@code "id"}
     * @param fieldValue   value that was looked up
     */
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(ErrorCode.RESOURCE_NOT_FOUND, resourceName, fieldName, fieldValue);
    }
}
