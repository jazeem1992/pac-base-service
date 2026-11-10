package com.allianz.pac.baseservice.config;

import com.allianz.pac.baseservice.util.LogSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async} for time-consuming operations.
 *
 * <p>The executor is Spring Boot's auto-configured {@code applicationTaskExecutor}, configured via
 * {@code spring.task.execution.*} and running on virtual threads when
 * {@code spring.threads.virtual.enabled=true}. The {@code MdcTaskDecorator} propagates the
 * correlation id to async threads.
 */
@Slf4j
@EnableAsync
@Configuration(proxyBeanMethods = false)
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Logs exceptions thrown by {@code void} async methods, which would otherwise be lost.
     * (Methods returning {@code CompletableFuture} propagate exceptions to the caller instead.)
     *
     * @return the uncaught exception handler
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("Async method {}.{}() failed: {}",
                method.getDeclaringClass().getSimpleName(), method.getName(), LogSanitizer.clean(ex.getMessage()), ex);
    }
}
