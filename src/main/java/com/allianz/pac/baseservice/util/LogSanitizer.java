package com.allianz.pac.baseservice.util;

import com.allianz.pac.baseservice.common.constants.AppConstants;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Makes values safe to write into logs.
 *
 * <ul>
 *   <li>removes CR/LF characters to prevent log injection (Sonar rule S5145),</li>
 *   <li>masks configured sensitive fields (e.g. {@code email=****}),</li>
 *   <li>truncates very long values.</li>
 * </ul>
 */
public final class LogSanitizer {

    private static final Pattern LINE_BREAKS = Pattern.compile("[\\r\\n]");

    /**
     * Prevents instantiation of this utility class.
     */
    private LogSanitizer() {
        throw new UnsupportedOperationException("Utility class must not be instantiated");
    }

    /**
     * Removes line breaks from a value so it cannot forge additional log lines.
     *
     * @param value value to clean, may be {@code null}
     * @return the cleaned value, or {@code "null"} for {@code null}
     */
    public static String clean(Object value) {
        if (value == null) {
            return "null";
        }
        return LINE_BREAKS.matcher(String.valueOf(value)).replaceAll("_");
    }

    /**
     * Cleans, masks and truncates a value for logging.
     *
     * @param value        value to sanitize (usually a method argument or result)
     * @param maskedFields field names whose values must be masked (case-insensitive)
     * @param maxLength    maximum length of the returned text
     * @return the sanitized text
     */
    public static String sanitize(Object value, Collection<String> maskedFields, int maxLength) {
        String text = clean(value);
        if (maskedFields != null && !maskedFields.isEmpty()) {
            text = buildMaskPattern(maskedFields).matcher(text).replaceAll("$1$2" + AppConstants.MASK);
        }
        if (text.length() > maxLength) {
            return text.substring(0, maxLength) + "...(truncated)";
        }
        return text;
    }

    /**
     * Builds a pattern matching {@code field=value} and {@code "field":"value"} pairs.
     *
     * @param maskedFields field names to match
     * @return compiled case-insensitive pattern
     */
    private static Pattern buildMaskPattern(Collection<String> maskedFields) {
        String names = maskedFields.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        return Pattern.compile("((?:" + names + ")\"?)(\\s*[=:]\\s*\"?)[^,\\]}\"\\s)]+", Pattern.CASE_INSENSITIVE);
    }
}
