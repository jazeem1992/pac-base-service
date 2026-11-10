package com.allianz.pac.baseservice.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a bean (type level) or a single method into START/END execution logging.
 *
 * <p>Controllers, services, clients and repositories are logged automatically by
 * {@code LoggingAspect}; use this annotation for Spring beans in any other package.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Loggable {
}
