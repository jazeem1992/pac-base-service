package com.allianz.pac.baseservice.common.logging;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

/**
 * Copies the logging MDC (correlation id, ...) from the submitting thread to the thread executing
 * an {@code @Async} task, so asynchronous logs stay correlated with the originating request.
 *
 * <p>Spring Boot applies every {@link TaskDecorator} bean to the auto-configured application task
 * executor automatically.
 */
@Component
public class MdcTaskDecorator implements TaskDecorator {

    /**
     * Wraps the task so that it runs with a copy of the caller's MDC and restores the worker's MDC
     * afterwards.
     *
     * @param runnable task submitted to the executor
     * @return the decorated task
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            applyContext(callerContext);
            try {
                runnable.run();
            } finally {
                applyContext(previous);
            }
        };
    }

    /**
     * Replaces the MDC of the current thread.
     *
     * @param context context to apply; {@code null} clears the MDC
     */
    private static void applyContext(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
