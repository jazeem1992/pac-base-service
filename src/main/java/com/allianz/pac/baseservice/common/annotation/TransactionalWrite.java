package com.allianz.pac.baseservice.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-write transaction with the project defaults: rolls back on ANY exception (checked or
 * unchecked) and uses the timeout configured in {@code app.transaction.timeout-seconds}.
 *
 * <p>Every attribute can be overridden per method, e.g.
 * {@code @TransactionalWrite(timeoutString = "${app.transaction.long-running-timeout-seconds}")}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Transactional
public @interface TransactionalWrite {

    /**
     * Transaction propagation behaviour.
     *
     * @return propagation, {@link Propagation#REQUIRED} by default
     */
    @AliasFor(annotation = Transactional.class, attribute = "propagation")
    Propagation propagation() default Propagation.REQUIRED;

    /**
     * Transaction isolation level.
     *
     * @return isolation, database default by default
     */
    @AliasFor(annotation = Transactional.class, attribute = "isolation")
    Isolation isolation() default Isolation.DEFAULT;

    /**
     * Transaction timeout in seconds; supports property placeholders.
     *
     * @return timeout expression
     */
    @AliasFor(annotation = Transactional.class, attribute = "timeoutString")
    String timeoutString() default "${app.transaction.timeout-seconds}";

    /**
     * Exception types that trigger a rollback.
     *
     * @return rollback exception types, all exceptions by default
     */
    @AliasFor(annotation = Transactional.class, attribute = "rollbackFor")
    Class<? extends Throwable>[] rollbackFor() default Exception.class;

    /**
     * Exception types that must NOT trigger a rollback.
     *
     * @return no-rollback exception types, none by default
     */
    @AliasFor(annotation = Transactional.class, attribute = "noRollbackFor")
    Class<? extends Throwable>[] noRollbackFor() default {};
}
