package com.allianz.pac.baseservice.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.allianz.pac.baseservice.common.annotation.Loggable;
import com.allianz.pac.baseservice.support.TestFixtures;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

/**
 * Unit tests for {@link LoggingAspect} using a programmatic AspectJ proxy.
 */
class LoggingAspectTest {

    /**
     * Sample bean opted into logging with {@link Loggable}.
     */
    @Loggable
    static class SampleBean {

        /**
         * Returns a greeting.
         *
         * @param name name to greet
         * @return greeting text
         */
        public String greet(String name) {
            return "Hello " + name;
        }

        /**
         * Always fails.
         */
        public void fail() {
            throw new IllegalStateException("boom");
        }

        /**
         * Sleeps briefly to exceed the slow threshold.
         *
         * @return a constant
         * @throws InterruptedException when interrupted
         */
        public String slow() throws InterruptedException {
            Thread.sleep(20);
            return "done";
        }
    }

    /**
     * Creates a proxy of {@link SampleBean} advised by the logging aspect.
     *
     * @param slowThreshold slow execution threshold
     * @return the advised proxy
     */
    private static SampleBean proxy(Duration slowThreshold) {
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleBean());
        factory.setProxyTargetClass(true);
        factory.addAspect(new LoggingAspect(TestFixtures.loggingProperties(slowThreshold)));
        return factory.getProxy();
    }

    /**
     * Verifies that the advised method still returns its result.
     */
    @Test
    @DisplayName("returns the result of the intercepted method")
    void returnsResult() {
        assertThat(proxy(Duration.ofSeconds(5)).greet("PAC")).isEqualTo("Hello PAC");
    }

    /**
     * Verifies that exceptions are logged and rethrown unchanged.
     */
    @Test
    @DisplayName("rethrows exceptions unchanged")
    void rethrowsExceptions() {
        SampleBean bean = proxy(Duration.ofSeconds(5));

        assertThatThrownBy(bean::fail).isInstanceOf(IllegalStateException.class).hasMessage("boom");
    }

    /**
     * Verifies the slow-call path (WARN log) does not change the result.
     *
     * @throws InterruptedException when interrupted
     */
    @Test
    @DisplayName("handles calls slower than the threshold")
    void handlesSlowCalls() throws InterruptedException {
        assertThat(proxy(Duration.ofMillis(1)).slow()).isEqualTo("done");
    }
}
