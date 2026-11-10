package com.allianz.pac.baseservice.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only transaction with the timeout configured in {@code app.transaction.timeout-seconds}.
 *
 * <p>Read-only transactions let Hibernate skip dirty checking and allow the driver to route to read
 * replicas, which matters under high load.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Transactional(readOnly = true)
public @interface TransactionalRead {

    /**
     * Transaction timeout in seconds; supports property placeholders.
     *
     * @return timeout expression
     */
    @AliasFor(annotation = Transactional.class, attribute = "timeoutString")
    String timeoutString() default "${app.transaction.timeout-seconds}";
}
