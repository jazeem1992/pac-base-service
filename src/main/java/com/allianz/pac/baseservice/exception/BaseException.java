package com.allianz.pac.baseservice.exception;

import java.io.Serial;
import java.util.Arrays;
import java.util.Map;
import lombok.Getter;

/**
 * Root of all application exceptions.
 *
 * <p>Carries an {@link ErrorCode} (HTTP status + message key), the arguments used to render the
 * localized message, and optional details returned in the {@code data} field of the response.
 * All application exceptions are unchecked, so they trigger transaction rollback automatically.
 */
@Getter
public abstract class BaseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final transient Object[] messageArgs;
    private final transient Map<String, String> details;

    /**
     * Creates an exception without additional details.
     *
     * @param errorCode   error code describing the failure
     * @param messageArgs arguments of the localized message
     */
    protected BaseException(ErrorCode errorCode, Object... messageArgs) {
        this(errorCode, Map.of(), null, messageArgs);
    }

    /**
     * Creates an exception with details and an optional root cause.
     *
     * @param errorCode   error code describing the failure
     * @param details     details returned to the client in {@code data}, may be {@code null}
     * @param cause       root cause, may be {@code null}
     * @param messageArgs arguments of the localized message
     */
    protected BaseException(ErrorCode errorCode, Map<String, String> details, Throwable cause, Object... messageArgs) {
        super(errorCode.name() + " " + Arrays.toString(messageArgs), cause);
        this.errorCode = errorCode;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    /**
     * Returns a defensive copy of the message arguments.
     *
     * @return message arguments
     */
    public Object[] getMessageArgs() {
        return messageArgs.clone();
    }
}
