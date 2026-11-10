package com.allianz.pac.baseservice.common.logging;

import com.allianz.pac.baseservice.BaseServiceApplication;
import com.allianz.pac.baseservice.config.properties.LoggingProperties;
import com.allianz.pac.baseservice.util.LogSanitizer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * Writes START / END logs (with execution time) for every method of controllers, services, clients
 * and repositories, plus any bean or method annotated with
 * {@link com.allianz.pac.baseservice.common.annotation.Loggable}.
 *
 * <p>Developers therefore never write manual "method start/end" log statements. Behaviour is
 * controlled by {@code app.logging.*}:
 * <ul>
 *   <li>{@code method-log-level} - level of START/END lines,</li>
 *   <li>{@code log-arguments} - arguments/results at DEBUG, sanitized and masked,</li>
 *   <li>{@code slow-execution-threshold} - slower calls are logged at WARN.</li>
 * </ul>
 * The logger name is the class of the intercepted bean, so levels can be tuned per package.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class LoggingAspect {

    private static final String BASE_PACKAGE = BaseServiceApplication.class.getPackageName();

    private final LoggingProperties loggingProperties;

    /**
     * Matches all methods of the controller layer.
     */
    @Pointcut("within(com.allianz.pac.baseservice.controller..*)")
    public void controllerLayer() {
        // pointcut signature only
    }

    /**
     * Matches all methods of the service layer.
     */
    @Pointcut("within(com.allianz.pac.baseservice.service..*)")
    public void serviceLayer() {
        // pointcut signature only
    }

    /**
     * Matches all methods of the outbound HTTP client layer.
     */
    @Pointcut("within(com.allianz.pac.baseservice.client..*)")
    public void clientLayer() {
        // pointcut signature only
    }

    /**
     * Matches all Spring Data repositories (including inherited CRUD methods).
     */
    @Pointcut("target(org.springframework.data.repository.Repository)")
    public void repositoryLayer() {
        // pointcut signature only
    }

    /**
     * Matches beans or methods explicitly annotated with {@code @Loggable}.
     */
    @Pointcut("@within(com.allianz.pac.baseservice.common.annotation.Loggable) "
            + "|| @annotation(com.allianz.pac.baseservice.common.annotation.Loggable)")
    public void loggableMarked() {
        // pointcut signature only
    }

    /**
     * Logs START, END (with duration) or FAILED for the intercepted method.
     *
     * @param joinPoint intercepted method invocation
     * @return the result of the intercepted method
     * @throws Throwable any exception thrown by the intercepted method, rethrown unchanged
     */
    @Around("controllerLayer() || serviceLayer() || clientLayer() || repositoryLayer() || loggableMarked()")
    // Must catch Throwable to log and rethrow whatever the intercepted method throws (rethrown unchanged).
    @SuppressWarnings({"java:S112", "java:S1181", "checkstyle:IllegalCatch"})
    public Object logExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        Class<?> targetType = resolveTargetType(joinPoint);
        Logger logger = LoggerFactory.getLogger(targetType);
        String method = targetType.getSimpleName() + "." + joinPoint.getSignature().getName() + "()";

        logger.atLevel(loggingProperties.methodLogLevel()).log("START {}", method);
        if (loggingProperties.logArguments() && logger.isDebugEnabled()) {
            logger.debug("ARGS {} -> {}", method, sanitize(Arrays.asList(joinPoint.getArgs())));
        }

        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            logEnd(logger, method, elapsedMillis(start));
            if (loggingProperties.logArguments() && logger.isDebugEnabled()) {
                logger.debug("RESULT {} -> {}", method, sanitize(result));
            }
            return result;
        } catch (Throwable ex) {
            logger.warn("FAILED {} after {} ms - {}: {}", method, elapsedMillis(start),
                    ex.getClass().getSimpleName(), LogSanitizer.clean(ex.getMessage()));
            throw ex;
        }
    }

    /**
     * Writes the END line, escalating to WARN when the call exceeded the slow threshold.
     *
     * @param logger    logger of the intercepted class
     * @param method    method label
     * @param elapsedMs execution time in milliseconds
     */
    private void logEnd(Logger logger, String method, long elapsedMs) {
        long thresholdMs = loggingProperties.slowExecutionThreshold().toMillis();
        if (elapsedMs > thresholdMs) {
            logger.warn("END {} - {} ms (SLOW, threshold {} ms)", method, elapsedMs, thresholdMs);
        } else {
            logger.atLevel(loggingProperties.methodLogLevel()).log("END {} - {} ms", method, elapsedMs);
        }
    }

    /**
     * Resolves the application class of the intercepted bean. For Spring Data repositories this is
     * the repository interface declared in this project rather than the generated proxy.
     *
     * @param joinPoint intercepted method invocation
     * @return the class used for the logger name and log labels
     */
    private Class<?> resolveTargetType(ProceedingJoinPoint joinPoint) {
        Object target = joinPoint.getTarget();
        if (target == null) {
            return joinPoint.getSignature().getDeclaringType();
        }
        Class<?> targetClass = AopUtils.getTargetClass(target);
        if (targetClass.getName().startsWith(BASE_PACKAGE)) {
            return targetClass;
        }
        Object proxy = joinPoint.getThis() != null ? joinPoint.getThis() : target;
        return Arrays.stream(ClassUtils.getAllInterfaces(proxy))
                .filter(type -> type.getName().startsWith(BASE_PACKAGE))
                .findFirst()
                .orElse(targetClass);
    }

    /**
     * Sanitizes a value for logging using the configured masked fields and maximum length.
     *
     * @param value value to sanitize
     * @return log-safe text
     */
    private String sanitize(Object value) {
        return LogSanitizer.sanitize(value, loggingProperties.maskedFields(), loggingProperties.maxArgumentLength());
    }

    /**
     * Computes elapsed milliseconds since the given {@link System#nanoTime()} value.
     *
     * @param startNanos start time in nanoseconds
     * @return elapsed time in milliseconds
     */
    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
