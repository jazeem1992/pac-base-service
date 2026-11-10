package com.allianz.pac.baseservice.common.logging;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Logs the start and the shutdown of the service process with its key runtime information.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationLifecycleLogger {

    private static final String UNKNOWN = "unknown";

    private final Environment environment;
    private final ObjectProvider<BuildProperties> buildProperties;

    /**
     * Logs that the application is fully started and ready to serve traffic.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("APPLICATION STARTED - name={}, version={}, profiles={}, port={}, java={}, virtualThreads={}",
                environment.getProperty("spring.application.name", UNKNOWN),
                resolveVersion(),
                Arrays.toString(environment.getActiveProfiles()),
                environment.getProperty("local.server.port", UNKNOWN),
                Runtime.version(),
                environment.getProperty("spring.threads.virtual.enabled", "false"));
    }

    /**
     * Logs that the application context is closing (graceful shutdown started).
     */
    @EventListener(ContextClosedEvent.class)
    public void onShutdown() {
        log.info("APPLICATION SHUTTING DOWN - name={}", environment.getProperty("spring.application.name", UNKNOWN));
    }

    /**
     * Reads the build version from {@code META-INF/build-info.properties}.
     *
     * @return the build version, or {@code unknown} when no build info is available
     */
    private String resolveVersion() {
        BuildProperties properties = buildProperties.getIfAvailable();
        return properties != null ? properties.getVersion() : UNKNOWN;
    }
}
