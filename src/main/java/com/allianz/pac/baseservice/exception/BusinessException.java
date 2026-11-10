package com.allianz.pac.baseservice.exception;

import java.io.Serial;
import java.util.Map;

/**
 * Thrown when a business rule is violated (HTTP 422 by default).
 *
 * <p>Use this for domain validations that cannot be expressed with Bean Validation annotations.
 */
public class BusinessException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a business exception for a business rule violation.
     *
     * @param ruleDescription description of the violated rule, used as message argument
     */
    public BusinessException(String ruleDescription) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, ruleDescription);
    }

    /**
     * Creates a business exception with a specific error code and details.
     *
     * @param errorCode   error code to return
     * @param details     details returned to the client in {@code data}
     * @param messageArgs arguments of the localized message
     */
    public BusinessException(ErrorCode errorCode, Map<String, String> details, Object... messageArgs) {
        super(errorCode, details, null, messageArgs);
    }
}
