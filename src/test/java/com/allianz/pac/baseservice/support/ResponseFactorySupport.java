package com.allianz.pac.baseservice.support;

import com.allianz.pac.baseservice.common.response.ApiResponseFactory;
import com.allianz.pac.baseservice.util.MessageResolver;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Builds an {@link ApiResponseFactory} backed by the real {@code messages.properties} for unit tests.
 */
public final class ResponseFactorySupport {

    /**
     * Prevents instantiation of this utility class.
     */
    private ResponseFactorySupport() {
        throw new UnsupportedOperationException("Utility class must not be instantiated");
    }

    /**
     * Creates a response factory using the application message bundle.
     *
     * @return the response factory
     */
    public static ApiResponseFactory responseFactory() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return new ApiResponseFactory(new MessageResolver(messageSource));
    }
}
