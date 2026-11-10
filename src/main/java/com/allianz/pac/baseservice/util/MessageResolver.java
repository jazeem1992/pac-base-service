package com.allianz.pac.baseservice.util;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves user-facing messages from {@code messages*.properties} using the request locale.
 */
@Component
@RequiredArgsConstructor
public class MessageResolver {

    private final MessageSource messageSource;

    /**
     * Resolves a message for the current locale.
     *
     * <p>If the key is missing the key itself is returned, so a missing translation never breaks a
     * response.
     *
     * @param key  message key
     * @param args message arguments substituted into {@code {0}}, {@code {1}}, ...
     * @return the resolved message text
     */
    public String resolve(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}
