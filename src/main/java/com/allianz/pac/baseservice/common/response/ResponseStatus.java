package com.allianz.pac.baseservice.common.response;

/**
 * Outcome of an API call, written to the {@code status} field of every {@link ApiResponse}.
 */
public enum ResponseStatus {
    /** The request was processed successfully. */
    SUCCESS,
    /** The request failed; {@code statusCode} and {@code message} describe why. */
    FAILURE
}
