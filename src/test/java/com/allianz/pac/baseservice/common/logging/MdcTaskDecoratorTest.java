package com.allianz.pac.baseservice.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Unit tests for {@link MdcTaskDecorator}.
 */
class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    /**
     * Clears the MDC after each test.
     */
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /**
     * Verifies that the caller's MDC is visible inside the task running on another thread.
     *
     * @throws InterruptedException when the worker thread is interrupted
     */
    @Test
    @DisplayName("propagates the caller MDC to the worker thread")
    void propagatesMdcToWorkerThread() throws InterruptedException {
        MDC.put("correlationId", "abc");
        AtomicReference<String> seen = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> seen.set(MDC.get("correlationId")));
        MDC.clear();

        Thread worker = Thread.ofVirtual().start(decorated);
        worker.join();

        assertThat(seen.get()).isEqualTo("abc");
    }

    /**
     * Verifies that the worker's previous MDC is restored after the task.
     */
    @Test
    @DisplayName("restores the previous MDC after the task")
    void restoresPreviousContext() {
        Runnable decorated = decorator.decorate(() -> assertThat(MDC.get("correlationId")).isNull());
        MDC.put("correlationId", "worker-value");

        decorated.run();

        assertThat(MDC.get("correlationId")).isEqualTo("worker-value");
    }
}
